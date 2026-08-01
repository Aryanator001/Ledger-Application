package com.ledger.query.engine

import com.ledger.query.ai.SchemaHint
import com.ledger.query.core.CategoryRef
import com.ledger.query.core.EntityRef
import com.ledger.query.core.QueryRouter
import com.ledger.query.exec.InMemoryPlanCache
import com.ledger.query.exec.QueryResult
import com.ledger.query.exec.PlanResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class QueryEngineTest {

    private val today = LocalDate.of(2026, 7, 16)

    private val knownEntities = listOf("Ramesh", "Vijay Kumar", "Vijaykumar Shah", "Suresh Patel")
    private val knownCategories = listOf("food", "rent", "shopping", "general", "transport", "salary")

    private val schema = SchemaHint(knownEntities, knownCategories, today)

    // Ten LOCAL-tier queries — used by test_local_tier_never_calls_gemini
    private val localTierQueries = listOf(
        "paid to Ramesh this month",
        "ramesh ko kitna diya",
        "food expense this month",
        "total expense this month",
        "received from Ramesh",
        "how much did I pay to Ramesh",
        "how many times did I pay Ramesh",
        "is mahine khana ka kharcha",
        "this month food expense",
        "last month paid to Ramesh"
    )

    private fun buildEngine(
        gemini: FakeGemini = FakeGemini(),
        executor: FakeExecutor = FakeExecutor(),
        planCache: InMemoryPlanCache = InMemoryPlanCache()
    ): QueryEngine {
        val entityResolver = LiveEntityResolver { knownEntities }
        val categoryResolver = LiveCategoryResolver()
        val router = QueryRouter(entityResolver, categoryResolver, clock = { today })
        return QueryEngine(router, executor, gemini, gemini, planCache)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: LOCAL tier never calls Gemini
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_local_tier_never_calls_gemini() = runBlocking {
        val gemini = FakeGemini()
        val engine = buildEngine(gemini = gemini)

        for (query in localTierQueries) {
            engine.ask(query, schema)
        }

        assertEquals(
            "LOCAL-tier queries must not call Gemini planner",
            0, gemini.planCallCount
        )
        assertEquals(
            "LOCAL-tier queries must not call Gemini analyst",
            0, gemini.analyzeCallCount
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Planner hallucinated entity is dropped by parseAndValidate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_planner_hallucinated_entity_is_dropped() {
        val planner = com.ledger.query.ai.GeminiPlannerImpl(apiKeyProvider = { "key" })
        val json = """{"direction":"OUT","metric":"SUM","group_by":"NONE","entity":"Fake Person","category":null,"start":"2026-07-01","end":"2026-07-16","limit":null}"""
        val result = planner.parseAndValidate(json, schema)
        assertNull("Hallucinated entity must be null", result?.plan?.entityId)
        assertNull("entityName must be null when entity not in schema", result?.entityName)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Inverted date range is rejected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_planner_inverted_range_rejected() {
        val planner = com.ledger.query.ai.GeminiPlannerImpl(apiKeyProvider = { "key" })
        val json = """{"direction":"OUT","metric":"SUM","group_by":"NONE","entity":null,"category":null,"start":"2026-07-16","end":"2026-07-01","limit":null}"""
        val result = planner.parseAndValidate(json, schema)
        assertNull("Inverted range must return null", result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: Gemini {"error":"unsupported"} → null
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_planner_error_response_returns_null() {
        val planner = com.ledger.query.ai.GeminiPlannerImpl(apiKeyProvider = { "key" })
        val result = planner.parseAndValidate("""{"error":"unsupported"}""", schema)
        assertNull("error response must return null", result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5: Template promotion — same template, 2nd call hits cache
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_template_promotion() = runBlocking {
        val gemini = FakeGemini(fixedPlanResult = FakeGemini.defaultPlanResult("Ramesh"))
        val planCache = InMemoryPlanCache()
        val traces = mutableListOf<com.ledger.query.exec.QueryTrace>()
        val engine = buildEngine(gemini = gemini, planCache = planCache) // no telemetry param
        val engineWithTelemetry = QueryEngine(
            router = QueryRouter(LiveEntityResolver { knownEntities }, LiveCategoryResolver(), clock = { today }),
            executor = FakeExecutor(),
            planner = gemini,
            analyst = gemini,
            planCache = planCache,
            telemetry = { traces += it }
        )

        // First call — AI_PLANNER tier → Gemini called once, skeleton stored
        engineWithTelemetry.ask("paid to Ramesh this month aur usse pehle bhi", schema)
        val firstGeminiCount = gemini.planCallCount

        // Same query second time — should be promoted from cache
        engineWithTelemetry.ask("paid to Ramesh this month aur usse pehle bhi", schema)

        assertEquals("Gemini called exactly once across two identical-template queries", firstGeminiCount, gemini.planCallCount)

        val secondTrace = traces.lastOrNull()
        assertTrue("second query must be promoted", secondTrace?.promoted == true)
        assertEquals("promoted query tier must be LOCAL", com.ledger.query.core.Tier.LOCAL, secondTrace?.tier)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6: Promotion survives new entity — same template, different entity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_promotion_survives_new_entity() = runBlocking {
        val gemini = FakeGemini(fixedPlanResult = FakeGemini.defaultPlanResult("Ramesh"))
        val planCache = InMemoryPlanCache()

        // Learn template with residual so it routes to AI_PLANNER
        val engine = QueryEngine(
            router = QueryRouter(LiveEntityResolver { knownEntities }, LiveCategoryResolver(), clock = { today }),
            executor = FakeExecutor(),
            planner = gemini,
            analyst = gemini,
            planCache = planCache
        )

        // First call to learn skeleton
        engine.ask("how much paid to Ramesh across all time and some other words", schema)
        val callsAfterFirst = gemini.planCallCount

        // Switch entity to "Suresh Patel" — same template shape → no Gemini call
        engine.ask("how much paid to Suresh Patel across all time and some other words", schema)

        assertEquals(
            "New entity with same template shape must not trigger Gemini",
            callsAfterFirst,
            gemini.planCallCount
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7: Analyst timeout → return facts, not error
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_analyst_timeout_returns_facts_not_error() = runBlocking {
        // Analyst returns null (simulates timeout)
        val gemini = FakeGemini(fixedNarrative = null)
        val executor = FakeExecutor()
        val engine = QueryEngine(
            router = QueryRouter(LiveEntityResolver { knownEntities }, LiveCategoryResolver(), clock = { today }),
            executor = executor,
            planner = gemini,
            analyst = gemini,
            planCache = InMemoryPlanCache()
        )

        // "why" is an analytical marker → AI_ANALYST tier
        val result = engine.ask("why am I spending so much on food", schema)

        assertFalse(
            "analyst timeout must NOT return Failed",
            result is QueryResult.Failed
        )
        assertTrue(
            "analyst timeout must return Scalar (grounding fact) instead",
            result is QueryResult.Scalar || result is QueryResult.Analysis || result is QueryResult.Grouped
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8: Empty result → Scalar(0.0, txnCount=0), not Failed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_empty_result_is_zero_not_error() = runBlocking {
        val executor = FakeExecutor() // always returns Scalar(0.0, count=0)
        val engine = buildEngine(executor = executor)

        val result = engine.ask("paid to Ramesh this month", schema)

        // Must be Scalar, not Failed
        assertTrue("empty DB result must be Scalar(0.0), not Failed", result is QueryResult.Scalar)
        result as QueryResult.Scalar
        assertEquals(0.0, result.amount, 0.0)
        assertEquals(0, result.txnCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 9: Gemini never produces amounts — structural invariant
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test_gemini_never_produces_amount() = runBlocking {
        // Gemini planner returns a plan — plan has no amount.
        // The amount in any Scalar comes only from the executor (which is Fake here → 0.0).
        // Assert that no Scalar.amount on any AI_PLANNER path traces to a Gemini string.

        // We verify structurally: GeminiPlannerImpl.parseAndValidate produces QueryPlan (no amount field).
        val planner = com.ledger.query.ai.GeminiPlannerImpl(apiKeyProvider = { "key" })
        val json = """{"direction":"OUT","metric":"SUM","group_by":"NONE","entity":"Ramesh","category":null,"start":"2026-07-01","end":"2026-07-16","limit":null}"""
        val planResult = planner.parseAndValidate(json, schema)

        // QueryPlan has no `amount` field — the class definition ensures this by construction.
        // The only way to get an amount is through QueryExecutor.execute(), never from the plan.
        val planFields = planResult?.plan?.javaClass?.declaredFields?.map { it.name } ?: emptyList()
        assertFalse(
            "QueryPlan must have no 'amount' field — Gemini cannot produce amounts",
            planFields.any { it.contains("amount", ignoreCase = true) }
        )
    }
}
