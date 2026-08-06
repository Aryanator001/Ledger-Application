package com.example.analytics

import com.example.ai.model.ParsedQuery
import com.example.ai.query.QueryAggregate
import com.example.ai.query.TimeToken
import com.example.ai.query.TimeTokenResolver

/**
 * Lookup-only API the QueryEngine consults before falling through
 * to direct Room aggregation.
 */
class AnalyticsRepository(
    private val analyticsCacheDao: AnalyticsCacheDao,
    private val keyResolver: AnalyticsKeyResolver,
    private val timeTokenResolver: TimeTokenResolver
) {

    suspend fun tryGet(query: ParsedQuery, timeToken: TimeToken): QueryAggregate? {
        val key = keyResolver.resolveKey(query, timeToken) ?: return null
        val cached = analyticsCacheDao.get(key) ?: return null

        // Bucket rollover detection
        val (currentStart, _) = timeTokenResolver.bucketBoundaries(timeToken)
        if (cached.bucketStartMs != currentStart) {
            analyticsCacheDao.delete(key)
            return null
        }

        return QueryAggregate.fromDouble(
            cached.totalAmount,
            cached.count,
            cached.transactionIds.split(",").filter { it.isNotBlank() }
        )
    }

    /**
     * Populate cache after a Room aggregation. Called by QueryEngine on cache miss.
     * Skips uncacheable keys silently.
     */
    suspend fun put(
        query: ParsedQuery,
        timeToken: TimeToken,
        aggregate: QueryAggregate
    ) {
        val key = keyResolver.resolveKey(query, timeToken) ?: return
        val (bucketStart, bucketEnd) = timeTokenResolver.bucketBoundaries(timeToken)

        analyticsCacheDao.upsert(AnalyticsCache(
            key = key,
            totalAmount = aggregate.totalAmount,
            count = aggregate.count,
            transactionIds = aggregate.transactionIds.joinToString(","),
            bucketStartMs = bucketStart,
            bucketEndMs = bucketEnd
        ))
    }
}
