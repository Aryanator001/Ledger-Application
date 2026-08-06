package com.ledger.query.exec

import com.example.data.Transaction
import com.example.data.TransactionDao
import com.ledger.query.core.Direction
import com.ledger.query.core.GroupBy
import com.ledger.query.core.Lang
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryPlan
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset

// ─────────────────────────────────────────────────────────────
// ResolvedQueryPlan — plan + the string values needed for SQL
// ─────────────────────────────────────────────────────────────

data class ResolvedQueryPlan(
    val plan: QueryPlan,
    /** LOWER(title) LIKE entityPrefix (e.g. "ramesh%") — null = no entity filter */
    val entityPrefix: String?,
    /** DB category_id string (e.g. "food") — null = no category filter */
    val categoryDbId: String?,
    /** Payment method filter ("cash" | "upi" | "bank") — null = no filter */
    val paymentMethod: String? = null
)

// ─────────────────────────────────────────────────────────────
// Interface
// ─────────────────────────────────────────────────────────────

interface QueryExecutor {
    suspend fun execute(resolved: ResolvedQueryPlan): QueryResult
    suspend fun gatherContext(frame: QueryFrame): List<QueryResult>
}

// ─────────────────────────────────────────────────────────────
// RoomQueryExecutor
// ─────────────────────────────────────────────────────────────

class RoomQueryExecutor(
    private val dao: TransactionDao,
    private val scopeIdProvider: () -> String
) : QueryExecutor {

    override suspend fun execute(resolved: ResolvedQueryPlan): QueryResult {
        val plan = resolved.plan
        val entityPrefix = resolved.entityPrefix?.lowercase()?.let { "$it%" }
        val categoryDbId = resolved.categoryDbId
        val paymentMethod = resolved.paymentMethod
        val dirType = plan.direction.toSqlDir()
        val startMs = plan.start.toStartMs()
        val endMs = plan.end.toEndMs()
        val scopeId = scopeIdProvider()
        val label = plan.label.ifBlank { buildLabel(resolved) }

        return when (plan.groupBy) {
            GroupBy.NONE -> executeScalar(plan, entityPrefix, categoryDbId, paymentMethod, dirType, startMs, endMs, scopeId, label)
            GroupBy.CATEGORY -> executeGroupByCategory(plan, entityPrefix, paymentMethod, dirType, startMs, endMs, scopeId, label)
            GroupBy.ENTITY -> executeGroupByEntity(plan, categoryDbId, paymentMethod, dirType, startMs, endMs, scopeId, label)
            GroupBy.DAY, GroupBy.WEEK, GroupBy.MONTH ->
                // Simplified: return flat scalar until time-bucketed queries are added
                executeScalar(plan, entityPrefix, categoryDbId, paymentMethod, dirType, startMs, endMs, scopeId, label)
        }
    }

    private suspend fun executeScalar(
        plan: QueryPlan,
        entityPrefix: String?,
        categoryDbId: String?,
        paymentMethod: String?,
        dirType: String?,
        startMs: Long,
        endMs: Long,
        scopeId: String,
        label: String
    ): QueryResult {
        return when (plan.metric) {
            Metric.LIST -> {
                val limit = plan.limit ?: 100
                val rows = dao.execList(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId, limit)
                QueryResult.Rows(
                    transactions = rows.map { it.toSummary() },
                    label = label,
                    plan = plan
                )
            }
            Metric.AVG -> {
                val value = dao.execAvg(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId) ?: 0.0
                val cntRow = dao.execScalar(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                val ids = fetchIds(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                QueryResult.Scalar(value, Metric.AVG, label, cntRow?.cnt ?: 0, plan, ids, formatTimeContext(startMs, endMs))
            }
            Metric.MAX -> {
                val value = dao.execMax(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId) ?: 0.0
                val cntRow = dao.execScalar(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                val ids = fetchIds(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                QueryResult.Scalar(value, Metric.MAX, label, cntRow?.cnt ?: 0, plan, ids, formatTimeContext(startMs, endMs))
            }
            Metric.MIN -> {
                val value = dao.execMin(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId) ?: 0.0
                val cntRow = dao.execScalar(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                val ids = fetchIds(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                QueryResult.Scalar(value, Metric.MIN, label, cntRow?.cnt ?: 0, plan, ids, formatTimeContext(startMs, endMs))
            }
            Metric.SUM, Metric.COUNT -> {
                val row = if (plan.direction == Direction.NET) {
                    dao.execNetScalar(entityPrefix, categoryDbId, paymentMethod, startMs, endMs, scopeId)
                } else {
                    dao.execScalar(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                }
                val total = row?.total ?: 0.0
                val cnt = row?.cnt ?: 0
                val amount = if (plan.metric == Metric.COUNT) cnt.toDouble() else total
                val ids = fetchIds(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
                QueryResult.Scalar(amount, plan.metric, label, cnt, plan, ids, formatTimeContext(startMs, endMs))
            }
        }
    }

    private suspend fun fetchIds(
        entityPrefix: String?,
        categoryDbId: String?,
        dirType: String?,
        paymentMethod: String?,
        startMs: Long,
        endMs: Long,
        scopeId: String
    ): List<String> {
        val row = dao.execIdList(entityPrefix, categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId)
        return row?.idList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    private suspend fun executeGroupByCategory(
        plan: QueryPlan,
        entityPrefix: String?,
        paymentMethod: String?,
        dirType: String?,
        startMs: Long,
        endMs: Long,
        scopeId: String,
        label: String
    ): QueryResult {
        val rows = dao.execGroupByCategory(entityPrefix, dirType, paymentMethod, startMs, endMs, scopeId)
        if (rows.isEmpty()) return QueryResult.Scalar(0.0, plan.metric, label, 0, plan, timeContext = formatTimeContext(startMs, endMs))
        val buckets = rows.map { QueryResult.Grouped.Bucket(it.categoryId, it.total, it.cnt) }
        return QueryResult.Grouped(buckets, label, plan, timeContext = formatTimeContext(startMs, endMs))
    }

    private suspend fun executeGroupByEntity(
        plan: QueryPlan,
        categoryDbId: String?,
        paymentMethod: String?,
        dirType: String?,
        startMs: Long,
        endMs: Long,
        scopeId: String,
        label: String
    ): QueryResult {
        val limit = plan.limit ?: 10
        val rows = dao.execGroupByEntity(categoryDbId, dirType, paymentMethod, startMs, endMs, scopeId, limit)
        if (rows.isEmpty()) return QueryResult.Scalar(0.0, plan.metric, label, 0, plan, timeContext = formatTimeContext(startMs, endMs))
        val buckets = rows.map { QueryResult.Grouped.Bucket(it.title, it.total, it.cnt) }
        return QueryResult.Grouped(buckets, label, plan, timeContext = formatTimeContext(startMs, endMs))
    }

    // ─── gatherContext — 5 aggregates in parallel ─────────────────────────────

    override suspend fun gatherContext(frame: QueryFrame): List<QueryResult> = coroutineScope {
        val entityPrefix = frame.entity?.canonicalName?.lowercase()?.let { "$it%" }
        val categoryDbId = frame.category?.let { canonicalToDbId(it.canonicalName) }
        val paymentMethod = frame.paymentMethod
        val dirType = frame.direction.toSqlDir()
        val startMs = frame.timeRange.start.toStartMs()
        val endMs = frame.timeRange.end.toEndMs()
        val scopeId = scopeIdProvider()

        // Prior period: shift back by the length of the current range
        val rangeDays = java.time.temporal.ChronoUnit.DAYS.between(frame.timeRange.start, frame.timeRange.end) + 1
        val priorStart = frame.timeRange.start.minusDays(rangeDays)
        val priorEnd = frame.timeRange.start.minusDays(1)
        val priorStartMs = priorStart.toStartMs()
        val priorEndMs = priorEnd.toEndMs()

        val planCurrent = QueryPlan(frame.direction, frame.metric, start = frame.timeRange.start, end = frame.timeRange.end, label = "Current period")
        val planPrior = QueryPlan(frame.direction, frame.metric, start = priorStart, end = priorEnd, label = "Prior period")
        val planCatBreakdown = QueryPlan(frame.direction, Metric.SUM, GroupBy.CATEGORY, start = frame.timeRange.start, end = frame.timeRange.end, label = "By category")
        val planEntityTop5 = QueryPlan(frame.direction, Metric.SUM, GroupBy.ENTITY, limit = 5, start = frame.timeRange.start, end = frame.timeRange.end, label = "Top 5 parties")
        val planCount = QueryPlan(frame.direction, Metric.COUNT, start = frame.timeRange.start, end = frame.timeRange.end, label = "Count")

        val currentDeferred = async {
            execute(ResolvedQueryPlan(planCurrent, entityPrefix, categoryDbId, paymentMethod))
        }
        val priorDeferred = async {
            val row = if (frame.direction == Direction.NET) {
                dao.execNetScalar(entityPrefix, categoryDbId, paymentMethod, priorStartMs, priorEndMs, scopeId)
            } else {
                dao.execScalar(entityPrefix, categoryDbId, dirType, paymentMethod, priorStartMs, priorEndMs, scopeId)
            }
            QueryResult.Scalar(row?.total ?: 0.0, frame.metric, "Prior period", row?.cnt ?: 0, planPrior)
        }
        val catBreakdownDeferred = async {
            execute(ResolvedQueryPlan(planCatBreakdown, entityPrefix, null, paymentMethod))
        }
        val entityTop5Deferred = async {
            execute(ResolvedQueryPlan(planEntityTop5, null, categoryDbId, paymentMethod))
        }
        val countDeferred = async {
            execute(ResolvedQueryPlan(planCount, entityPrefix, categoryDbId, paymentMethod))
        }

        listOf(
            currentDeferred.await(),
            priorDeferred.await(),
            catBreakdownDeferred.await(),
            entityTop5Deferred.await(),
            countDeferred.await()
        )
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun Direction.toSqlDir(): String? = when (this) {
        Direction.IN -> "IN"
        Direction.OUT -> "OUT"
        Direction.NET, Direction.UNSPECIFIED -> null
    }

    private fun LocalDate.toStartMs(): Long =
        atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun LocalDate.toEndMs(): Long =
        plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1L

    private fun formatTimeContext(startMs: Long, endMs: Long): String {
        if (startMs <= 0L) return "All time"
        val zone = java.time.ZoneId.systemDefault()
        val startDate = java.time.Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val endDate = java.time.Instant.ofEpochMilli(minOf(endMs, System.currentTimeMillis())).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now()
        return when {
            startDate.withDayOfMonth(1) == today.withDayOfMonth(1) && endDate >= today ->
                "This month"
            startDate == java.time.LocalDate.of(today.year, 1, 1) && endDate >= today ->
                "This year"
            startDate.year == endDate.year && startDate.monthValue == endDate.monthValue ->
                startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
            else ->
                "${startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))} – ${endDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))}"
        }
    }

    private fun buildLabel(resolved: ResolvedQueryPlan): String {
        val plan = resolved.plan
        val parts = mutableListOf<String>()
        parts += when (plan.direction) {
            Direction.OUT -> "Paid"
            Direction.IN -> "Received"
            Direction.NET -> "Balance"
            Direction.UNSPECIFIED -> "Activity"
        }
        resolved.entityPrefix?.removeSuffix("%")?.let { parts += it.replaceFirstChar { c -> c.uppercase() } }
        resolved.categoryDbId?.let { parts += it.replaceFirstChar { c -> c.uppercase() } }
        return parts.joinToString(" · ")
    }

    private fun Transaction.toSummary(): QueryResult.Rows.TxnSummary {
        val dir = if (type == "EXPENSE") Direction.OUT else Direction.IN
        val date = LocalDate.ofEpochDay(this.date / 86400000L)
        return QueryResult.Rows.TxnSummary(
            id = id,
            date = date,
            partyName = title.takeIf { it.isNotBlank() },
            categoryName = categoryId,
            amount = amount,
            direction = dir
        )
    }
}

internal fun canonicalToDbId(canonicalName: String?): String? = when (canonicalName?.lowercase()) {
    "food" -> "food"
    "rent" -> "rent"
    "shopping" -> "shopping"
    "general" -> "general"
    "transport" -> "transport"
    "salary" -> "salary"
    else -> canonicalName?.lowercase()
}
