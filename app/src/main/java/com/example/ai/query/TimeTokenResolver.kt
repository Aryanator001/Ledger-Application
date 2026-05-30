package com.example.ai.query

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Resolves a TimeToken to a concrete (startMs, endMs) range in IST.
 * LIFETIME returns (0L, Long.MAX_VALUE) so the DAO's BETWEEN filter is a no-op.
 */
class TimeTokenResolver(
    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
    private val clock: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Kolkata")) }
) {

    fun resolve(token: TimeToken): Pair<Long, Long> {
        val today = clock()
        return when (token) {
            TimeToken.TODAY -> dayRange(today)
            TimeToken.YESTERDAY -> dayRange(today.minusDays(1))
            TimeToken.THIS_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6)
                rangeBetween(monday, sunday)
            }
            TimeToken.LAST_WEEK -> {
                val lastMonday = today
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .minusWeeks(1)
                val lastSunday = lastMonday.plusDays(6)
                rangeBetween(lastMonday, lastSunday)
            }
            TimeToken.THIS_MONTH -> {
                val first = today.withDayOfMonth(1)
                val last = today.with(TemporalAdjusters.lastDayOfMonth())
                rangeBetween(first, last)
            }
            TimeToken.LAST_MONTH -> {
                val lastMonthSameDay = today.minusMonths(1)
                val first = lastMonthSameDay.withDayOfMonth(1)
                val last = lastMonthSameDay.with(TemporalAdjusters.lastDayOfMonth())
                rangeBetween(first, last)
            }
            TimeToken.LIFETIME -> 0L to Long.MAX_VALUE
            TimeToken.CUSTOM -> 0L to Long.MAX_VALUE
        }
    }

    private fun dayRange(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    private fun rangeBetween(startDate: LocalDate, endDate: LocalDate): Pair<Long, Long> {
        val start = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = endDate.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    /**
     * Resolve explicit YYYY-MM-DD date strings (from Gemini) to a (startMs, endMs) range.
     * Returns LIFETIME range if parsing fails.
     */
    fun resolveCustom(startDate: String, endDate: String): Pair<Long, Long> {
        return try {
            val start = java.time.LocalDate.parse(startDate)
            val end = java.time.LocalDate.parse(endDate)
            val startMs = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMs = end.atTime(java.time.LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
            startMs to endMs
        } catch (e: Exception) {
            0L to Long.MAX_VALUE
        }
    }

    /**
     * Returns the cache-bucket boundaries for a given TimeToken.
     * Used by AnalyticsCache to detect bucket rollover at read time.
     *
     * For LIFETIME and CUSTOM, returns (0L, Long.MAX_VALUE) — these are
     * not cached (LIFETIME entity balances are the exception, handled
     * by the key resolver).
     */
    fun bucketBoundaries(token: TimeToken): Pair<Long, Long> {
        return when (token) {
            TimeToken.LIFETIME, TimeToken.CUSTOM -> 0L to Long.MAX_VALUE
            else -> resolve(token)
        }
    }

    /**
     * True if a TimeToken's bucket is stable enough to cache without
     * unbounded growth. LIFETIME and CUSTOM are excluded — but entity
     * balances are an explicit LIFETIME exception in the key resolver.
     */
    fun isCacheable(token: TimeToken): Boolean {
        return token != TimeToken.LIFETIME && token != TimeToken.CUSTOM
    }

    /**
     * Returns a human-readable label for the headline template.
     * THIS_MONTH → "this month", LAST_WEEK → "last week", LIFETIME → "" (no label).
     */
    fun label(token: TimeToken): String = when (token) {
        TimeToken.TODAY -> "today"
        TimeToken.YESTERDAY -> "yesterday"
        TimeToken.THIS_WEEK -> "this week"
        TimeToken.LAST_WEEK -> "last week"
        TimeToken.THIS_MONTH -> "this month"
        TimeToken.LAST_MONTH -> "last month"
        TimeToken.LIFETIME -> ""
        TimeToken.CUSTOM -> "in the selected period"
    }
}
