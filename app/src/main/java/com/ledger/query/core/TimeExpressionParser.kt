package com.ledger.query.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

object TimeExpressionParser {

    data class Match(val range: TimeRange, val consumedIndices: Set<Int>)

    // Never call LocalDate.now() here — today is always injected so tests are deterministic.
    fun parse(tokens: List<String>, today: LocalDate): Match? {
        // Scan most-specific first: 3-token → 2-token → 1-token.
        // Return on first hit to avoid double-consuming tokens.

        // Pass 1: 3-token numeric-offset patterns
        //   A: [determiner] [number] [unit]  — "last 3 months", "pichle 7 din"
        //   B: [number] [unit] [offset]      — "3 days ago", "3 din pehle"
        for (i in 0..tokens.size - 3) {
            tryThreeToken(tokens, i, today)?.let { return it }
        }

        // Pass 2: 2-token patterns
        //   [this/last/next] [unit]  — "this month", "pichle hafte"
        //   [monthName] [year]       — "january 2025"
        for (i in 0..tokens.size - 2) {
            tryTwoToken(tokens, i, today)?.let { return it }
        }

        // Pass 3: single-token
        for (i in tokens.indices) {
            tryOneToken(tokens[i], i, today)?.let { return it }
        }

        return null
    }

    // -------------------------------------------------------------------------
    // Three-token
    // -------------------------------------------------------------------------

    private fun tryThreeToken(tokens: List<String>, i: Int, today: LocalDate): Match? {
        val t0 = tokens[i]; val t1 = tokens[i + 1]; val t2 = tokens[i + 2]

        // Pattern A: past/future determiner + number + unit
        val detA = asPastFutureDet(t0)
        val numA = t1.toIntOrNull()
        val unitA = asUnit(t2)
        if (detA != null && numA != null && numA > 0 && unitA != null) {
            val range = buildNOffset(detA, numA, unitA, today)
            return Match(range, setOf(i, i + 1, i + 2))
        }

        // Pattern B: number + unit + offset marker
        // Bare "3 months rent" has no offset marker — correctly returns null here.
        val numB = t0.toIntOrNull()
        val unitB = asUnit(t1)
        val offsetB = asPastFutureOffset(t2)
        if (numB != null && numB > 0 && unitB != null && offsetB != null) {
            val range = buildNOffset(offsetB, numB, unitB, today)
            return Match(range, setOf(i, i + 1, i + 2))
        }

        return null
    }

    // -------------------------------------------------------------------------
    // Two-token
    // -------------------------------------------------------------------------

    private fun tryTwoToken(tokens: List<String>, i: Int, today: LocalDate): Match? {
        val t0 = tokens[i]; val t1 = tokens[i + 1]

        // this/last/next + unit
        val det2 = asPeriodDet(t0)
        val unit2 = asUnit(t1)
        if (det2 != null && unit2 != null) {
            val range = buildPeriodRange(det2, unit2, today)
            return Match(range, setOf(i, i + 1))
        }

        // month name + 4-digit year
        val month2 = asMonthName(t0)
        val year2 = t1.toIntOrNull()
        if (month2 != null && year2 != null && year2 in 2000..2099) {
            val start = LocalDate.of(year2, month2, 1)
            val end = start.withDayOfMonth(start.lengthOfMonth())
            return Match(TimeRange(start, end, "${MONTH_NAMES[month2.value]} $year2"), setOf(i, i + 1))
        }

        return null
    }

    // -------------------------------------------------------------------------
    // Single-token
    // -------------------------------------------------------------------------

    private fun tryOneToken(token: String, i: Int, today: LocalDate): Match? {
        return when (token) {
            "today", "aaj", "aaje", "આજે", "aaaj" ->
                Match(TimeRange(today, today, "Today"), setOf(i))

            "yesterday" ->
                Match(TimeRange(today.minusDays(1), today.minusDays(1), "Yesterday"), setOf(i))

            // kal = yesterday in this accounting ledger context.
            // Hindi "kal" is ambiguous (yesterday / tomorrow) but an accounting ledger is
            // almost always queried in the past tense ("kal kitna diya" = "what did I pay yesterday").
            // Resolving to "yesterday" avoids showing future dates in historical reports.
            "kal", "kaal" ->
                Match(TimeRange(today.minusDays(1), today.minusDays(1), "Yesterday"), setOf(i))

            "tomorrow", "kal_future" ->
                Match(TimeRange(today.plusDays(1), today.plusDays(1), "Tomorrow"), setOf(i))

            "lifetime", "abtak", "ever", "alltime", "sada", "हमेशा" ->
                Match(TimeRange.lifetime(today), setOf(i))

            else -> {
                val month = asMonthName(token) ?: return null
                // If the named month is still in the future for this year, assume last year.
                val year = if (month > today.month) today.year - 1 else today.year
                val start = LocalDate.of(year, month, 1)
                val end = start.withDayOfMonth(start.lengthOfMonth())
                Match(TimeRange(start, end, "${MONTH_NAMES[month.value]} $year"), setOf(i))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Range builders
    // -------------------------------------------------------------------------

    private fun buildNOffset(direction: String, n: Int, unit: TimeUnit, today: LocalDate): TimeRange {
        return when (direction) {
            "PAST" -> {
                val start = when (unit) {
                    // "last N days" is inclusive of today: 7 days = today-6 .. today (7 days total)
                    TimeUnit.DAY -> today.minusDays((n - 1).toLong())
                    TimeUnit.WEEK -> today.minusWeeks(n.toLong())
                    TimeUnit.MONTH -> today.minusMonths(n.toLong())
                    TimeUnit.YEAR -> today.minusYears(n.toLong())
                }
                TimeRange(start, today, "Last $n ${unit.plural(n)}")
            }
            "FUTURE" -> {
                val end = when (unit) {
                    TimeUnit.DAY -> today.plusDays(n.toLong())
                    TimeUnit.WEEK -> today.plusWeeks(n.toLong())
                    TimeUnit.MONTH -> today.plusMonths(n.toLong())
                    TimeUnit.YEAR -> today.plusYears(n.toLong())
                }
                TimeRange(today, end, "Next $n ${unit.plural(n)}")
            }
            else -> TimeRange.lifetime(today)
        }
    }

    private fun buildPeriodRange(det: String, unit: TimeUnit, today: LocalDate): TimeRange {
        return when (det) {
            "THIS" -> when (unit) {
                TimeUnit.DAY -> TimeRange(today, today, "Today")
                TimeUnit.WEEK -> {
                    val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    TimeRange(mon, today, "This week")
                }
                TimeUnit.MONTH -> {
                    val start = today.withDayOfMonth(1)
                    TimeRange(start, today, "This month")
                }
                TimeUnit.YEAR -> {
                    val start = today.withDayOfYear(1)
                    TimeRange(start, today, "This year")
                }
            }
            "LAST" -> when (unit) {
                TimeUnit.DAY -> {
                    val yd = today.minusDays(1)
                    TimeRange(yd, yd, "Yesterday")
                }
                TimeUnit.WEEK -> {
                    val thisWeekMon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val lastWeekMon = thisWeekMon.minusWeeks(1)
                    val lastWeekSun = thisWeekMon.minusDays(1)
                    TimeRange(lastWeekMon, lastWeekSun, "Last week")
                }
                TimeUnit.MONTH -> {
                    val firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1)
                    val lastOfLastMonth = firstOfLastMonth.withDayOfMonth(firstOfLastMonth.lengthOfMonth())
                    TimeRange(firstOfLastMonth, lastOfLastMonth, "Last month")
                }
                TimeUnit.YEAR -> {
                    val start = today.minusYears(1).withDayOfYear(1)
                    val end = start.withDayOfYear(start.lengthOfYear())
                    TimeRange(start, end, "Last year")
                }
            }
            "NEXT" -> when (unit) {
                TimeUnit.DAY -> TimeRange(today.plusDays(1), today.plusDays(1), "Tomorrow")
                TimeUnit.WEEK -> {
                    val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    val nextSun = nextMon.plusDays(6)
                    TimeRange(nextMon, nextSun, "Next week")
                }
                TimeUnit.MONTH -> {
                    val start = today.plusMonths(1).withDayOfMonth(1)
                    val end = start.withDayOfMonth(start.lengthOfMonth())
                    TimeRange(start, end, "Next month")
                }
                TimeUnit.YEAR -> {
                    val start = LocalDate.of(today.year + 1, 1, 1)
                    val end = start.withDayOfYear(start.lengthOfYear())
                    TimeRange(start, end, "Next year")
                }
            }
            else -> TimeRange.lifetime(today)
        }
    }

    // -------------------------------------------------------------------------
    // Token classifiers
    // -------------------------------------------------------------------------

    private enum class TimeUnit {
        DAY, WEEK, MONTH, YEAR;
        fun plural(n: Int) = when (this) {
            DAY -> if (n == 1) "day" else "days"
            WEEK -> if (n == 1) "week" else "weeks"
            MONTH -> if (n == 1) "month" else "months"
            YEAR -> if (n == 1) "year" else "years"
        }
    }

    private fun asUnit(t: String): TimeUnit? = when (t) {
        "day", "days", "din", "दिन", "divas" -> TimeUnit.DAY
        "week", "weeks", "hafte", "hafta", "हफ्ते" -> TimeUnit.WEEK
        "month", "months", "mahine", "mahina", "maheena", "महीने", "mahino" -> TimeUnit.MONTH
        "year", "years", "saal", "sal", "varsh", "साल", "वर्ष", "varsh" -> TimeUnit.YEAR
        else -> null
    }

    // Determiners for N-offset patterns: "last 3", "pichle 7", "next 5"
    private fun asPastFutureDet(t: String): String? = when (t) {
        "last", "pichle", "pichli", "pichhle", "past", "recent", "bita", "bite" -> "PAST"
        "next", "agle", "agli", "agla", "coming", "upcoming", "aata" -> "FUTURE"
        else -> null
    }

    // Offset markers for N-unit-offset patterns: "3 days ago", "3 din pehle"
    private fun asPastFutureOffset(t: String): String? = when (t) {
        "ago", "pehle", "pahle", "पहले", "back", "purva" -> "PAST"
        "later", "baad", "बाद", "after", "hence" -> "FUTURE"
        else -> null
    }

    // Determiners for period patterns: "this week", "last month"
    private fun asPeriodDet(t: String): String? = when (t) {
        "this", "iss", "is", "current", "chal", "chalu", "ye", "yeh" -> "THIS"
        "last", "pichle", "pichli", "pichhle", "past", "bita", "bite", "gaya", "gaye" -> "LAST"
        "next", "agle", "agli", "agla", "coming", "upcoming" -> "NEXT"
        else -> null
    }

    private fun asMonthName(t: String): Month? = when (t) {
        "january", "jan" -> Month.JANUARY
        "february", "feb" -> Month.FEBRUARY
        "march", "mar" -> Month.MARCH
        "april", "apr" -> Month.APRIL
        "may" -> Month.MAY
        "june", "jun" -> Month.JUNE
        "july", "jul" -> Month.JULY
        "august", "aug" -> Month.AUGUST
        "september", "sep", "sept" -> Month.SEPTEMBER
        "october", "oct" -> Month.OCTOBER
        "november", "nov" -> Month.NOVEMBER
        "december", "dec" -> Month.DECEMBER
        else -> null
    }

    private val MONTH_NAMES = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
}
