package com.ledger.query.engine

import com.ledger.query.ai.AiAnalyst
import com.ledger.query.exec.LedgerLog
import com.ledger.query.ai.AiPlanner
import com.ledger.query.ai.SchemaHint
import com.ledger.query.core.ClarificationField
import com.ledger.query.core.Direction
import com.ledger.query.core.Lang
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryRouter
import com.ledger.query.core.Tier
import com.ledger.query.exec.InMemoryPlanCache
import com.ledger.query.exec.PlanCache
import com.ledger.query.exec.PlanSkeleton
import com.ledger.query.exec.QueryExecutor
import com.ledger.query.exec.QueryResult
import com.ledger.query.exec.QueryTrace
import com.ledger.query.exec.ResolvedQueryPlan
import com.ledger.query.exec.Templater
import com.ledger.query.exec.canonicalToDbId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

class QueryEngine(
    private val router: QueryRouter,
    private val executor: QueryExecutor,
    private val planner: AiPlanner,
    private val analyst: AiAnalyst,
    private val planCache: PlanCache,
    private val telemetry: (QueryTrace) -> Unit = {}
) {

    suspend fun ask(rawQuery: String, schema: SchemaHint): QueryResult {
        val wallStart = System.currentTimeMillis()

        // ── Step 1: Route ─────────────────────────────────────────────────────
        val parseStart = System.currentTimeMillis()
        val decision = router.route(rawQuery)
        val parseMs = System.currentTimeMillis() - parseStart
        val frame = decision.frame

        var aiMs = 0L
        var dbMs = 0L
        var planCacheHit = false
        var promoted = false

        val result: QueryResult = when (decision.tier) {

            // ── Tier A: LOCAL — execute directly ─────────────────────────────
            Tier.LOCAL -> {
                val plan = router.toPlan(frame)
                val resolved = frame.toResolved(plan.label)
                val dbStart = System.currentTimeMillis()
                val r = safeExecute(resolved, plan.label)
                dbMs = System.currentTimeMillis() - dbStart
                r
            }

            // ── CLARIFY — ask user to disambiguate ────────────────────────────
            Tier.CLARIFY -> {
                when (decision.fieldType) {
                    ClarificationField.TIME_RANGE -> {
                        val q = when (frame.language) {
                            Lang.HI -> "Kaunse time period ke liye?"
                            Lang.GU -> "Kya samay galo?"
                            else    -> "What time period?"
                        }
                        QueryResult.NeedsClarification(
                            question = q,
                            options = emptyList(),
                            fieldType = ClarificationField.TIME_RANGE,
                            presetAppendValues = listOf("this week", "this month", "last 3 months", "this year", "all time")
                        )
                    }
                    ClarificationField.ENTITY, null -> {
                        val entity = frame.entity?.surfaceForm ?: "entity"
                        val langQ = when (frame.language) {
                            Lang.HI -> "Kaun se $entity?"
                            Lang.GU -> "Kya $entity?"
                            else    -> "Which $entity?"
                        }
                        QueryResult.NeedsClarification(
                            question = langQ,
                            options = decision.clarifyOptions,
                            fieldType = ClarificationField.ENTITY,
                            presetAppendValues = decision.clarifyOptions,
                            surfaceForm = frame.entity?.surfaceForm
                        )
                    }
                }
            }

            // ── Tier B: AI_PLANNER — check cache first, then Gemini ──────────
            Tier.AI_PLANNER -> {
                val template = Templater.templatize(frame)
                val cached = planCache.get(template)

                if (cached != null) {
                    // Cache hit → promote to LOCAL
                    planCacheHit = true
                    promoted = true
                    LedgerLog.i("QueryEngine", "PROMOTED: '$template' served from plan cache")
                    val hydratedPlan = cached.hydrate(frame)
                    val resolved = frame.toResolved(hydratedPlan.label)
                    val dbStart = System.currentTimeMillis()
                    val r = safeExecute(resolved, hydratedPlan.label)
                    dbMs = System.currentTimeMillis() - dbStart
                    r
                } else {
                    // Cache miss → call Gemini planner
                    val aiStart = System.currentTimeMillis()
                    val planResult = withTimeoutOrNull(4_000L) {
                        withContext(Dispatchers.IO) { planner.plan(frame, schema) }
                    }
                    aiMs = System.currentTimeMillis() - aiStart

                    if (planResult == null) {
                        // Degrade gracefully: try executing with what the router gave us
                        val fallbackLabel = "Closest match for: $rawQuery"
                        val fallbackPlan = router.toPlan(frame)
                        val resolved = frame.toResolved(fallbackPlan.label)
                        val dbStart = System.currentTimeMillis()
                        val r = safeExecute(resolved, fallbackLabel)
                        dbMs = System.currentTimeMillis() - dbStart
                        r
                    } else {
                        // ← THE LEARNING STEP — store skeleton for future promotion
                        val skeleton = PlanSkeleton.from(planResult.plan)
                        planCache.put(template, skeleton)
                        LedgerLog.d("QueryEngine", "Learned template: '$template' → ${skeleton.direction}/${skeleton.metric}")

                        val resolved = ResolvedQueryPlan(
                            plan = planResult.plan,
                            entityPrefix = planResult.entityName?.lowercase()?.let { "$it%" },
                            categoryDbId = planResult.categoryDbId
                        )
                        val dbStart = System.currentTimeMillis()
                        val r = safeExecute(resolved, planResult.plan.label)
                        dbMs = System.currentTimeMillis() - dbStart
                        r
                    }
                }
            }

            // ── Tier C: AI_ANALYST — gather facts first, then ask model ──────
            Tier.AI_ANALYST -> {
                val dbStart = System.currentTimeMillis()
                val facts = try { executor.gatherContext(frame) } catch (e: Exception) {
                    LedgerLog.e("QueryEngine", "gatherContext failed", e)
                    emptyList()
                }
                dbMs = System.currentTimeMillis() - dbStart

                val aiStart = System.currentTimeMillis()
                val narrative = withTimeoutOrNull(8_000L) {
                    withContext(Dispatchers.IO) { analyst.analyze(frame, facts) }
                }
                aiMs = System.currentTimeMillis() - aiStart

                if (narrative != null) {
                    QueryResult.Analysis(narrative, facts)
                } else {
                    // Analyst timed out or failed — return real facts, not an error
                    LedgerLog.w("QueryEngine", "Analyst unavailable — returning grounding facts")
                    facts.firstOrNull()
                        ?: QueryResult.Failed("Could not analyze at this time", recoverable = true)
                }
            }
        }

        val totalMs = System.currentTimeMillis() - wallStart
        // When promoted, report the effective tier (LOCAL) not the original routing tier (AI_PLANNER)
        val reportedTier = if (promoted) Tier.LOCAL else decision.tier
        telemetry(
            QueryTrace(
                query = rawQuery,
                tier = reportedTier,
                routeReason = decision.reason,
                parseMs = parseMs,
                aiMs = aiMs,
                dbMs = dbMs,
                totalMs = totalMs,
                planCacheHit = planCacheHit,
                promoted = promoted
            )
        )
        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun safeExecute(resolved: ResolvedQueryPlan, fallbackLabel: String): QueryResult {
        return try {
            executor.execute(resolved)
        } catch (e: Exception) {
            LedgerLog.e("QueryEngine", "executor.execute failed", e)
            QueryResult.Failed(
                "Couldn't fully understand that. Showing closest match: $fallbackLabel",
                recoverable = false
            )
        }
    }

    /** Build a ResolvedQueryPlan from a frame, using its entity/category names. */
    private fun QueryFrame.toResolved(label: String = ""): ResolvedQueryPlan {
        val plan = router.toPlan(this).let {
            if (label.isNotBlank()) it.copy(label = label) else it
        }
        val entityPrefix = entity?.canonicalName?.lowercase()?.let { "$it%" }
        val categoryDbId = category?.let { canonicalToDbId(it.canonicalName) }
        return ResolvedQueryPlan(plan, entityPrefix, categoryDbId)
    }
}
