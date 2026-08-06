package com.ledger.query.exec

import com.ledger.query.core.Direction
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryPlan
import com.ledger.query.core.Tier
import java.time.LocalDate

/**
 * Output of AiPlanner — plan + string context needed for SQL execution.
 * Gemini produces entity/category names; the planner validates and passes them here.
 */
data class PlanResult(
    val plan: QueryPlan,
    /** Validated entity canonical name (e.g. "Ramesh") — null if no entity */
    val entityName: String?,
    /** DB category_id string (e.g. "food") — null if no category */
    val categoryDbId: String?
)

sealed class QueryResult {

    data class Scalar(
        val amount: Double,
        val metric: Metric,
        val label: String,
        val txnCount: Int,
        val plan: QueryPlan,
        val transactionIds: List<String> = emptyList(),
        val timeContext: String? = null
    ) : QueryResult()

    data class Grouped(
        val buckets: List<Bucket>,
        val label: String,
        val plan: QueryPlan,
        val transactionIds: List<String> = emptyList(),
        val timeContext: String? = null
    ) : QueryResult() {
        data class Bucket(val key: String, val amount: Double, val count: Int)
    }

    data class Rows(
        val transactions: List<TxnSummary>,
        val label: String,
        val plan: QueryPlan
    ) : QueryResult() {
        data class TxnSummary(
            val id: String,
            val date: LocalDate,
            val partyName: String?,
            val categoryName: String?,
            val amount: Double,
            val direction: Direction
        )
    }

    data class Analysis(
        val narrative: String,
        val groundingFacts: List<QueryResult>
    ) : QueryResult()

    data class NeedsClarification(
        val question: String,
        val options: List<String>
    ) : QueryResult()

    data class Failed(
        val reason: String,
        val recoverable: Boolean
    ) : QueryResult()
}

data class QueryTrace(
    val query: String,
    val tier: Tier,
    val routeReason: String,
    val parseMs: Long,
    val aiMs: Long,
    val dbMs: Long,
    val totalMs: Long,
    val planCacheHit: Boolean,
    val promoted: Boolean
)
