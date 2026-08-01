package com.ledger.query.engine

import com.example.data.TransactionDao
import com.ledger.query.ai.AiAnalyst
import com.ledger.query.ai.AiPlanner
import com.ledger.query.ai.GeminiAnalystImpl
import com.ledger.query.ai.GeminiPlannerImpl
import com.ledger.query.ai.SchemaHint
import com.ledger.query.core.CategoryRef
import com.ledger.query.core.EntityRef
import com.ledger.query.core.QueryRouter
import com.ledger.query.exec.PlanCache
import com.ledger.query.exec.QueryExecutor
import com.ledger.query.exec.QueryPlanCacheDao
import com.ledger.query.exec.RoomPlanCacheImpl
import com.ledger.query.exec.RoomQueryExecutor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
// LiveEntityResolver — reads from in-memory name list provided by ViewModel
// ─────────────────────────────────────────────────────────────

class LiveEntityResolver(
    private val namesProvider: () -> List<String>
) : QueryRouter.EntityResolver {

    override fun couldBeEntity(token: String): Boolean {
        val lower = token.lowercase()
        return namesProvider().any { name ->
            name.lowercase().split(" ").any { part ->
                part.startsWith(lower) || part == lower
            }
        }
    }

    override fun resolve(surfaceForm: String): EntityRef {
        val lower = surfaceForm.lowercase().trim()
        val names = namesProvider()

        val matches = names.filter { name ->
            val nameLower = name.lowercase()
            nameLower == lower ||
                nameLower.split(" ").any { part -> part.startsWith(lower) }
        }

        return when (matches.size) {
            0 -> EntityRef(surfaceForm, null, null, 0.0f)
            1 -> {
                val idx = names.indexOfFirst { it.equals(matches[0], ignoreCase = true) }
                EntityRef(surfaceForm, idx.toLong() + 1L, matches[0], 1.0f)
            }
            else -> EntityRef(
                surfaceForm,
                id = null,
                canonicalName = null,
                confidence = 0.5f,
                ambiguousWith = matches
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LiveCategoryResolver — static seeded category list
// ─────────────────────────────────────────────────────────────

class LiveCategoryResolver : QueryRouter.CategoryResolver {

    private val keywords: Map<String, Pair<Long, String>> = mapOf(
        "food"       to (10L to "Food"),
        "khana"      to (10L to "Food"),
        "khaana"     to (10L to "Food"),
        "groceries"  to (10L to "Food"),
        "grocery"    to (10L to "Food"),
        "rent"       to (11L to "Rent"),
        "kiraya"     to (11L to "Rent"),
        "kiraye"     to (11L to "Rent"),
        "shopping"   to (12L to "Shopping"),
        "kharidari"  to (12L to "Shopping"),
        "general"    to (1L  to "General"),
        "transport"  to (13L to "Transport"),
        "travel"     to (13L to "Transport"),
        "salary"     to (14L to "Salary")
    )

    override fun couldBeCategory(token: String): Boolean = token.lowercase() in keywords

    override fun resolve(surfaceForm: String): CategoryRef {
        val entry = keywords[surfaceForm.lowercase()]
        return if (entry != null)
            CategoryRef(surfaceForm, entry.first, entry.second, 1.0f)
        else
            CategoryRef(surfaceForm, null, null, 0.0f)
    }

    fun knownCategories(): List<String> =
        keywords.values.map { it.second }.distinct()
}

// ─────────────────────────────────────────────────────────────
// Factory function — manual wiring, no DI framework
// ─────────────────────────────────────────────────────────────

object QueryEngineFactory {

    private val sharedHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun create(
        transactionDao: TransactionDao,
        queryPlanCacheDao: QueryPlanCacheDao,
        scopeIdProvider: () -> String,
        apiKeyProvider: () -> String,
        entityNamesProvider: () -> List<String>,
        telemetry: (com.ledger.query.exec.QueryTrace) -> Unit = {}
    ): QueryEngine {
        val entityResolver = LiveEntityResolver(entityNamesProvider)
        val categoryResolver = LiveCategoryResolver()

        val router = QueryRouter(
            entityResolver = entityResolver,
            categoryResolver = categoryResolver
        )

        val executor: QueryExecutor = RoomQueryExecutor(transactionDao, scopeIdProvider)
        val planner: AiPlanner = GeminiPlannerImpl(sharedHttpClient, apiKeyProvider)
        val analyst: AiAnalyst = GeminiAnalystImpl(sharedHttpClient, apiKeyProvider)
        val planCache: PlanCache = RoomPlanCacheImpl(queryPlanCacheDao)

        return QueryEngine(
            router = router,
            executor = executor,
            planner = planner,
            analyst = analyst,
            planCache = planCache,
            telemetry = telemetry
        )
    }

    /** Helper: extract the category name list from a LiveCategoryResolver. */
    fun categoryNames(): List<String> = LiveCategoryResolver().knownCategories()
}
