package com.ledger.query.core

import java.time.LocalDate

enum class Direction { OUT, IN, NET, UNSPECIFIED }
enum class Metric { SUM, COUNT, AVG, MAX, MIN, LIST }
enum class GroupBy { NONE, ENTITY, CATEGORY, DAY, WEEK, MONTH }
enum class Lang { EN, HI, GU, MIXED }
enum class Tier { LOCAL, AI_PLANNER, AI_ANALYST, CLARIFY }

data class TimeRange(
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val isLifetime: Boolean = false
) {
    companion object {
        fun lifetime(today: LocalDate) =
            TimeRange(LocalDate.of(1970, 1, 1), today, "All time", isLifetime = true)
    }
}

data class EntityRef(
    val surfaceForm: String,
    val id: Long?,
    val canonicalName: String?,
    val confidence: Float,
    val ambiguousWith: List<String> = emptyList()
) {
    val isResolved: Boolean get() = id != null && ambiguousWith.isEmpty()
    val isAmbiguous: Boolean get() = ambiguousWith.isNotEmpty()
}

data class CategoryRef(
    val surfaceForm: String,
    val id: Long?,
    val canonicalName: String?,
    val confidence: Float
) {
    val isResolved: Boolean get() = id != null
}

data class QueryFrame(
    val rawQuery: String,
    val normalizedQuery: String,
    val language: Lang,
    val direction: Direction = Direction.UNSPECIFIED,
    val metric: Metric = Metric.SUM,
    val groupBy: GroupBy = GroupBy.NONE,
    val entity: EntityRef? = null,
    val category: CategoryRef? = null,
    val timeRange: TimeRange,
    val timeWasExplicit: Boolean = false,
    val hasMetricSignal: Boolean = false,
    val residual: List<String> = emptyList(),
    val consumed: Map<String, String> = emptyMap(),
    val analyticalMarkers: List<String> = emptyList(),
    val paymentMethod: String? = null
)

data class QueryPlan(
    val direction: Direction,
    val metric: Metric,
    val groupBy: GroupBy = GroupBy.NONE,
    val entityId: Long? = null,
    val categoryId: Long? = null,
    val start: LocalDate,
    val end: LocalDate,
    val limit: Int? = null,
    val label: String = ""
) {
    fun signature(): String = "$direction|$metric|$groupBy|e=$entityId|c=$categoryId|lim=$limit"
}

data class RouteDecision(
    val tier: Tier,
    val confidence: Float,
    val reason: String,
    val frame: QueryFrame,
    val clarifyOptions: List<String> = emptyList()
)
