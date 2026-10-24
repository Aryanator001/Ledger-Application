package com.ledger.query.engine

import com.ledger.query.ai.SchemaHint
import com.ledger.query.core.Direction
import com.ledger.query.core.GroupBy
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryRouter
import com.ledger.query.core.Tier
import com.ledger.query.exec.InMemoryPlanCache
import com.ledger.query.exec.PlanResult
import com.ledger.query.exec.PlanSkeleton
import com.ledger.query.exec.Templater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QueryExecutionRegressionTest {
    private val today = LocalDate.of(2026, 7, 16)
    private val schema = SchemaHint(listOf("Ramesh"), listOf("food"), today)
    private val router = QueryRouter(
        LiveEntityResolver { schema.knownEntities }, LiveCategoryResolver(), clock = { today }
    )

    @Test
    fun localExecutionPreservesPaymentMethod() = runBlocking {
        for (method in listOf("cash", "upi", "bank")) {
            val query = "total expense $method this month"
            assertEquals(Tier.LOCAL, router.route(query).tier)
            val executor = FakeExecutor()
            val gemini = FakeGemini()
            QueryEngine(router, executor, gemini, gemini, InMemoryPlanCache()).ask(query, schema)

            assertEquals(method, executor.executedPlans.single().paymentMethod)
            assertEquals(0, gemini.planCallCount)
        }
    }

    @Test
    fun plannerAndPromotedExecutionPreservePaymentMethod() = runBlocking {
        val query = "paid to Ramesh cash this month mystery"
        val frame = router.route(query).also { assertEquals(Tier.AI_PLANNER, it.tier) }.frame
        val plan = router.toPlan(frame)
        val gemini = FakeGemini(fixedPlanResult = PlanResult(plan, "Ramesh", null))
        val executor = FakeExecutor()
        val engine = QueryEngine(router, executor, gemini, gemini, InMemoryPlanCache())

        engine.ask(query, schema)
        engine.ask(query, schema)

        assertEquals(1, gemini.planCallCount)
        assertEquals(listOf("cash", "cash"), executor.executedPlans.map { it.paymentMethod })
    }

    @Test
    fun plannerFallbackPreservesPaymentMethod() = runBlocking {
        val query = "total expense upi this month mystery"
        assertEquals(Tier.AI_PLANNER, router.route(query).tier)
        val gemini = FakeGemini(fixedPlanResult = null)
        val executor = FakeExecutor()
        QueryEngine(router, executor, gemini, gemini, InMemoryPlanCache()).ask(query, schema)

        assertEquals("upi", executor.executedPlans.single().paymentMethod)
    }

    @Test
    fun promotedExecutionUsesLearnedSemanticsAndCurrentFiltersAndDates() = runBlocking {
        val query = "paid to Ramesh food this month mystery"
        val frame = router.route(query).also { assertEquals(Tier.AI_PLANNER, it.tier) }.frame
        val cache = InMemoryPlanCache()
        cache.put(Templater.templatize(frame), PlanSkeleton(
            Direction.IN, Metric.LIST, GroupBy.ENTITY,
            usesEntity = true, usesCategory = true, limit = 3
        ))
        val executor = FakeExecutor()
        val gemini = FakeGemini()
        QueryEngine(router, executor, gemini, gemini, cache).ask(query, schema)

        val resolved = executor.executedPlans.single()
        assertEquals(Direction.IN, resolved.plan.direction)
        assertEquals(Metric.LIST, resolved.plan.metric)
        assertEquals(GroupBy.ENTITY, resolved.plan.groupBy)
        assertEquals(3, resolved.plan.limit)
        assertEquals(frame.timeRange.start, resolved.plan.start)
        assertEquals(frame.timeRange.end, resolved.plan.end)
        assertEquals("ramesh%", resolved.entityPrefix)
        assertEquals("food", resolved.categoryDbId)
        assertEquals(0, gemini.planCallCount)
    }

    @Test
    fun promotedExecutionDoesNotReintroduceFiltersExcludedByLearnedPlan() = runBlocking {
        val query = "paid to Ramesh food this month mystery"
        val frame = router.route(query).also { assertEquals(Tier.AI_PLANNER, it.tier) }.frame
        val cache = InMemoryPlanCache()
        cache.put(Templater.templatize(frame), PlanSkeleton(
            Direction.OUT, Metric.SUM, GroupBy.NONE,
            usesEntity = false, usesCategory = false, limit = null
        ))
        val executor = FakeExecutor()
        val gemini = FakeGemini()
        QueryEngine(router, executor, gemini, gemini, cache).ask(query, schema)

        assertNull(executor.executedPlans.single().entityPrefix)
        assertNull(executor.executedPlans.single().categoryDbId)
    }
}
