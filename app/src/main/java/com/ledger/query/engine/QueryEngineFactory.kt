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
        if (lower.length < 2) return false
        return namesProvider().any { name ->
            val nl = name.lowercase()
            nl == lower || nl.split(" ").any { part -> part.startsWith(lower) } ||
                nl.startsWith(lower) || nl.contains(lower)
        }
    }

    override fun resolve(surfaceForm: String): EntityRef {
        val lower = surfaceForm.lowercase().trim()
        val names = namesProvider()

        // Tier 1: Full-name exact match — "ramesh" → "Ramesh", NOT "Ramesh Painter"
        // This prevents false ambiguity between "Ramesh" and "Ramesh Painter".
        val fullNameMatches = names.filter { it.lowercase() == lower }
        if (fullNameMatches.size == 1) {
            val idx = names.indexOfFirst { it.equals(fullNameMatches[0], ignoreCase = true) }
            return EntityRef(surfaceForm, idx.toLong() + 1L, fullNameMatches[0], 1.0f)
        }
        if (fullNameMatches.size > 1) {
            return EntityRef(surfaceForm, null, null, 0.5f, ambiguousWith = fullNameMatches)
        }

        // Tier 2: Word-level exact match — only reached when Tier 1 found nothing.
        // "vijay" → matches "Vijay Kumar" and "Vijay Shah" (both have "vijay" as a word) → genuinely ambiguous.
        val wordMatches = names.filter { name ->
            name.lowercase().split(" ").any { part -> part == lower }
        }
        if (wordMatches.size == 1) {
            val idx = names.indexOfFirst { it.equals(wordMatches[0], ignoreCase = true) }
            return EntityRef(surfaceForm, idx.toLong() + 1L, wordMatches[0], 0.95f)
        }
        if (wordMatches.size > 1) {
            return EntityRef(surfaceForm, null, null, 0.5f, ambiguousWith = wordMatches)
        }

        // Tier 3: Fuzzy — prefix / substring fallback
        val fuzzyMatches = names.filter { name ->
            val nl = name.lowercase()
            nl.split(" ").any { part -> part.startsWith(lower) } ||
                nl.startsWith(lower) || nl.contains(lower)
        }

        return when (fuzzyMatches.size) {
            0 -> EntityRef(surfaceForm, null, null, 0.3f)  // not found — Rule 0 triggers CLARIFY
            1 -> {
                val idx = names.indexOfFirst { it.equals(fuzzyMatches[0], ignoreCase = true) }
                EntityRef(surfaceForm, idx.toLong() + 1L, fuzzyMatches[0], 0.9f)
            }
            else -> EntityRef(surfaceForm, null, null, 0.5f, ambiguousWith = fuzzyMatches.take(4))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LiveCategoryResolver — static seeded category list
// ─────────────────────────────────────────────────────────────

class LiveCategoryResolver(
    private val dynamicNamesProvider: () -> List<String> = { emptyList() }
) : QueryRouter.CategoryResolver {

    private val staticKeywords: Map<String, Pair<Long, String>> = mapOf(
        "food"       to (10L to "food"),
        "khana"      to (10L to "food"),
        "khaana"     to (10L to "food"),
        "groceries"  to (10L to "food"),
        "grocery"    to (10L to "food"),
        "rent"       to (11L to "rent"),
        "kiraya"     to (11L to "rent"),
        "kiraye"     to (11L to "rent"),
        "shopping"   to (12L to "shopping"),
        "kharidari"  to (12L to "shopping"),
        "general"    to (1L  to "general"),
        "transport"  to (13L to "transport"),
        "travel"     to (13L to "transport"),
        "salary"     to (14L to "salary"),
        "electricity" to (15L to "electricity"),
        "bijli"      to (15L to "electricity"),
        "medical"    to (16L to "medical"),
        "health"     to (16L to "medical"),
        "education"  to (17L to "education"),
        "entertainment" to (18L to "entertainment")
    )

    override fun couldBeCategory(token: String): Boolean {
        val lower = token.lowercase()
        if (lower in staticKeywords) return true
        return dynamicNamesProvider().any { it.lowercase() == lower || it.lowercase().startsWith(lower) }
    }

    override fun resolve(surfaceForm: String): CategoryRef {
        val lower = surfaceForm.lowercase()
        val staticEntry = staticKeywords[lower]
        if (staticEntry != null) {
            return CategoryRef(surfaceForm, staticEntry.first, staticEntry.second, 1.0f)
        }
        // Check dynamic DB categories (exact name match → use name itself as DB id)
        val dynMatch = dynamicNamesProvider().firstOrNull { it.lowercase() == lower || it.lowercase().startsWith(lower) }
        if (dynMatch != null) {
            return CategoryRef(surfaceForm, 0L, dynMatch.lowercase(), 0.9f)
        }
        return CategoryRef(surfaceForm, null, null, 0.0f)
    }

    fun knownCategories(): List<String> =
        (staticKeywords.values.map { it.second } + dynamicNamesProvider()).distinct()
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
        categoryNamesProvider: () -> List<String> = { emptyList() },
        telemetry: (com.ledger.query.exec.QueryTrace) -> Unit = {}
    ): QueryEngine {
        val entityResolver = LiveEntityResolver(entityNamesProvider)
        val categoryResolver = LiveCategoryResolver(categoryNamesProvider)

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

    /** Helper: static category names (used before DB is loaded). */
    fun categoryNames(): List<String> = LiveCategoryResolver().knownCategories()
}
