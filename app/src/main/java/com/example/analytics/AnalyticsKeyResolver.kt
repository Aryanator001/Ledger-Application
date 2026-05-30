package com.example.analytics

import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery
import com.example.ai.query.TimeToken
import com.example.ai.query.TimeTokenResolver
import com.example.data.Transaction

/**
 * Single source of truth for cache key construction.
 * Keys are generic ("total_expense_this_month") — never embed the current
 * month/year. Bucket boundaries on the cached row define validity.
 *
 * Phase 6B caches 8 key shapes initially. Other queries fall through to
 * direct Room aggregation.
 */
class AnalyticsKeyResolver(
    private val timeTokenResolver: TimeTokenResolver
) {

    /**
     * Resolve a ParsedQuery to its cache key, or null if not cacheable.
     *
     * Cacheable patterns (initial 8):
     *   1. total_expense_today
     *   2. total_income_today
     *   3. total_expense_this_week
     *   4. total_expense_this_month
     *   5. total_income_this_month
     *   6. net_balance_this_month
     *   7. entity_balance:{lowercased_name}     (LIFETIME)
     *   8. category_total:{categoryId}_this_month
     */
    fun resolveKey(query: ParsedQuery, timeToken: TimeToken): String? {
        // Entity balance is the only LIFETIME-cacheable shape
        if (query.entity != null
            && query.direction == null
            && timeToken == TimeToken.LIFETIME) {
            return "entity_balance:${query.entity.lowercase()}"
        }

        // Everything else requires a stable time bucket
        if (!timeTokenResolver.isCacheable(timeToken)) return null

        val bucketSuffix = when (timeToken) {
            TimeToken.TODAY -> "today"
            TimeToken.THIS_WEEK -> "this_week"
            TimeToken.THIS_MONTH -> "this_month"
            else -> null
        }

        return when {
            // Total expense for today / this_week / this_month
            query.entity == null
                    && query.categoryHint == null
                    && query.direction == Direction.EXPENSE
                    && bucketSuffix != null ->
                "total_expense_$bucketSuffix"

            // Total income for today / this_month
            query.entity == null
                    && query.categoryHint == null
                    && query.direction == Direction.INCOME
                    && bucketSuffix in setOf("today", "this_month") ->
                "total_income_$bucketSuffix"

            // Net balance this month
            query.entity == null
                    && query.categoryHint == null
                    && query.direction == null
                    && timeToken == TimeToken.THIS_MONTH ->
                "net_balance_this_month"

            // Category total this month
            query.entity == null
                    && query.categoryHint != null
                    && timeToken == TimeToken.THIS_MONTH ->
                "category_total:${query.categoryHint}_this_month"

            else -> null
        }
    }

    /**
     * Compute the cache keys affected by a transaction change.
     * Used by AnalyticsCacheUpdater for write-through updates.
     *
     * Returns AffectedKey objects carrying the signed delta amount.
     */
    fun keysAffectedBy(transaction: Transaction): List<AffectedKey> {
        val keys = mutableListOf<AffectedKey>()
        val txDate = transaction.date

        val isExpense = transaction.type == "EXPENSE"
        val isIncome = transaction.type in setOf("INCOME", "CREDIT")
        val signedAmount = when {
            isIncome -> transaction.amount
            isExpense -> -transaction.amount
            else -> 0.0
        }
        val rawAmount = transaction.amount

        val (todayStart, todayEnd) = timeTokenResolver.bucketBoundaries(TimeToken.TODAY)
        val (weekStart, weekEnd) = timeTokenResolver.bucketBoundaries(TimeToken.THIS_WEEK)
        val (monthStart, monthEnd) = timeTokenResolver.bucketBoundaries(TimeToken.THIS_MONTH)

        val inToday = txDate in todayStart..todayEnd
        val inWeek = txDate in weekStart..weekEnd
        val inMonth = txDate in monthStart..monthEnd

        if (inToday && isExpense) keys.add(AffectedKey("total_expense_today", rawAmount, todayStart, todayEnd))
        if (inToday && isIncome) keys.add(AffectedKey("total_income_today", rawAmount, todayStart, todayEnd))
        if (inWeek && isExpense) keys.add(AffectedKey("total_expense_this_week", rawAmount, weekStart, weekEnd))
        if (inMonth && isExpense) keys.add(AffectedKey("total_expense_this_month", rawAmount, monthStart, monthEnd))
        if (inMonth && isIncome) keys.add(AffectedKey("total_income_this_month", rawAmount, monthStart, monthEnd))
        if (inMonth) keys.add(AffectedKey("net_balance_this_month", signedAmount, monthStart, monthEnd))
        if (inMonth && transaction.categoryId.isNotBlank()) {
            keys.add(AffectedKey("category_total:${transaction.categoryId}_this_month", rawAmount, monthStart, monthEnd))
        }
        if (transaction.title.isNotBlank()) {
            keys.add(AffectedKey("entity_balance:${transaction.title.lowercase()}", signedAmount, 0L, Long.MAX_VALUE))
        }

        return keys
    }
}

data class AffectedKey(
    val key: String,
    val signedDelta: Double,
    val bucketStartMs: Long,
    val bucketEndMs: Long
)
