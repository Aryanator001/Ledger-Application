package com.ledger.query.engine

import com.ledger.query.ai.AiAnalyst
import com.ledger.query.ai.AiPlanner
import com.ledger.query.ai.SchemaHint
import com.ledger.query.core.Direction
import com.ledger.query.core.GroupBy
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryPlan
import com.ledger.query.exec.PlanResult
import com.ledger.query.exec.QueryExecutor
import com.ledger.query.exec.QueryResult
import com.ledger.query.exec.ResolvedQueryPlan
import java.time.LocalDate

/**
 * Deterministic stand-in for both GeminiPlanner and GeminiAnalyst.
 * No network, no coroutine suspension beyond the minimum.
 */
class FakeGemini(
    /** If set, returned as the plan. If null, plan() returns null. */
    private val fixedPlanResult: PlanResult? = defaultPlanResult(),
    /** Narrative returned by analyze(). Null simulates timeout. */
    private val fixedNarrative: String? = "Simulated analysis"
) : AiPlanner, AiAnalyst {

    var planCallCount = 0
        private set
    var analyzeCallCount = 0
        private set

    override suspend fun plan(frame: QueryFrame, schema: SchemaHint): PlanResult? {
        planCallCount++
        return fixedPlanResult
    }

    override suspend fun analyze(frame: QueryFrame, facts: List<QueryResult>): String? {
        analyzeCallCount++
        return fixedNarrative
    }

    companion object {
        fun defaultPlanResult(
            entityName: String? = "Ramesh",
            categoryDbId: String? = null
        ): PlanResult {
            val today = LocalDate.of(2026, 7, 16)
            val plan = QueryPlan(
                direction = Direction.OUT,
                metric = Metric.SUM,
                groupBy = GroupBy.NONE,
                entityId = 3L,
                categoryId = null,
                start = LocalDate.of(1970, 1, 1),
                end = today,
                label = "Paid · Ramesh · All time"
            )
            return PlanResult(plan, entityName, categoryDbId)
        }

        /** Returns a PlanResult where Gemini hallucinated an entity not in the schema. */
        fun hallucinatedEntityResult(): PlanResult {
            val today = LocalDate.of(2026, 7, 16)
            val plan = QueryPlan(
                direction = Direction.OUT,
                metric = Metric.SUM,
                groupBy = GroupBy.NONE,
                entityId = 99L,
                categoryId = null,
                start = LocalDate.of(2026, 7, 1),
                end = today,
                label = "Paid · Fake Person · This month"
            )
            return PlanResult(plan, entityName = "Fake Person", categoryDbId = null)
        }

        /** Returns a PlanResult with end < start (inverted range). */
        fun invertedRangeResult(): PlanResult {
            val plan = QueryPlan(
                direction = Direction.OUT,
                metric = Metric.SUM,
                groupBy = GroupBy.NONE,
                entityId = null,
                categoryId = null,
                start = LocalDate.of(2026, 7, 16),
                end = LocalDate.of(2026, 7, 1),  // end < start
                label = "Inverted"
            )
            return PlanResult(plan, null, null)
        }
    }
}

/**
 * Simple in-memory executor that always returns a Scalar(0.0) with zero count.
 * Tracks call count so tests can assert "DB was called N times".
 */
class FakeExecutor : QueryExecutor {
    var executeCallCount = 0
        private set
    var gatherContextCallCount = 0
        private set

    override suspend fun execute(resolved: com.ledger.query.exec.ResolvedQueryPlan): QueryResult {
        executeCallCount++
        return QueryResult.Scalar(0.0, resolved.plan.metric, resolved.plan.label, 0, resolved.plan)
    }

    override suspend fun gatherContext(frame: QueryFrame): List<QueryResult> {
        gatherContextCallCount++
        return listOf(
            QueryResult.Scalar(1200.0, Metric.SUM, "Current period", 3,
                QueryPlan(Direction.OUT, Metric.SUM, start = frame.timeRange.start, end = frame.timeRange.end))
        )
    }
}
