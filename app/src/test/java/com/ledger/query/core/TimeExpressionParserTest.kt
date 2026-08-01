package com.ledger.query.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TimeExpressionParserTest {

    // All tests use a fixed reference date — deterministic, no wall-clock dependency.
    private val today = LocalDate.of(2026, 7, 16)  // Thursday

    private fun parse(vararg words: String) =
        TimeExpressionParser.parse(words.toList(), today)

    // -------------------------------------------------------------------------
    // This-period
    // -------------------------------------------------------------------------

    @Test fun thisMonth_en() {
        val m = parse("this", "month")!!
        assertEquals(LocalDate.of(2026, 7, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("This month", m.range.label)
        assertEquals(setOf(0, 1), m.consumedIndices)
    }

    @Test fun thisMonth_hi() {
        val m = parse("iss", "mahine")!!
        assertEquals(LocalDate.of(2026, 7, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("This month", m.range.label)
    }

    @Test fun thisWeek_en() {
        val m = parse("this", "week")!!
        assertEquals(LocalDate.of(2026, 7, 13), m.range.start)  // Monday
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("This week", m.range.label)
    }

    @Test fun thisYear_en() {
        val m = parse("this", "year")!!
        assertEquals(LocalDate.of(2026, 1, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("This year", m.range.label)
    }

    // -------------------------------------------------------------------------
    // Last-period
    // -------------------------------------------------------------------------

    @Test fun lastMonth_en() {
        val m = parse("last", "month")!!
        assertEquals(LocalDate.of(2026, 6, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 6, 30), m.range.end)
        assertEquals("Last month", m.range.label)
    }

    @Test fun lastMonth_hi() {
        val m = parse("pichle", "mahine")!!
        assertEquals(LocalDate.of(2026, 6, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 6, 30), m.range.end)
        assertEquals("Last month", m.range.label)
    }

    @Test fun lastWeek_en() {
        val m = parse("last", "week")!!
        assertEquals(LocalDate.of(2026, 7, 6), m.range.start)   // Monday of last week
        assertEquals(LocalDate.of(2026, 7, 12), m.range.end)    // Sunday of last week
        assertEquals("Last week", m.range.label)
    }

    @Test fun lastWeek_hi() {
        val m = parse("pichle", "hafte")!!
        assertEquals(LocalDate.of(2026, 7, 6), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 12), m.range.end)
        assertEquals("Last week", m.range.label)
    }

    // -------------------------------------------------------------------------
    // Numeric offset
    // -------------------------------------------------------------------------

    @Test fun last7Days_en() {
        val m = parse("last", "7", "days")!!
        assertEquals(LocalDate.of(2026, 7, 10), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("Last 7 days", m.range.label)
        assertEquals(setOf(0, 1, 2), m.consumedIndices)
    }

    @Test fun pichle3Mahine_hi() {
        val m = parse("pichle", "3", "mahine")!!
        assertEquals(LocalDate.of(2026, 4, 16), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("Last 3 months", m.range.label)
    }

    @Test fun dinPehle_hi() {
        val m = parse("3", "din", "pehle")!!
        assertEquals(LocalDate.of(2026, 7, 14), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 16), m.range.end)
        assertEquals("Last 3 days", m.range.label)
    }

    // -------------------------------------------------------------------------
    // Single-word
    // -------------------------------------------------------------------------

    @Test fun today_hi() {
        val m = parse("aaj")!!
        assertEquals(today, m.range.start)
        assertEquals(today, m.range.end)
        assertEquals("Today", m.range.label)
        assertEquals(setOf(0), m.consumedIndices)
    }

    @Test fun kal_resolvesYesterday() {
        // kal is ambiguous in Hindi but resolves to yesterday in accounting context
        val m = parse("kal")!!
        assertEquals(LocalDate.of(2026, 7, 15), m.range.start)
        assertEquals(LocalDate.of(2026, 7, 15), m.range.end)
        assertEquals("Yesterday", m.range.label)
    }

    @Test fun namedMonth_pastMonth() {
        val m = parse("march")!!
        assertEquals(LocalDate.of(2026, 3, 1), m.range.start)
        assertEquals(LocalDate.of(2026, 3, 31), m.range.end)
        assertEquals("March 2026", m.range.label)
    }

    // -------------------------------------------------------------------------
    // Null cases — must NOT match
    // -------------------------------------------------------------------------

    @Test fun noTimeExpression_returnsNull() {
        assertNull(parse("paid", "to", "vijay"))
    }

    @Test fun bareNumberUnit_returnsNull() {
        // "3 months" without a determiner or offset marker is not a time expression.
        // This prevents "3 months rent" from being misread as a date range.
        assertNull(parse("3", "months", "rent"))
    }

    // -------------------------------------------------------------------------
    // Token indices are correct
    // -------------------------------------------------------------------------

    @Test fun consumedIndices_inLargerSentence() {
        // "iss mahine mene x ko diya" — time is at index 0,1
        val tokens = listOf("iss", "mahine", "mene", "x", "ko", "diya")
        val m = TimeExpressionParser.parse(tokens, today)!!
        assertEquals(setOf(0, 1), m.consumedIndices)
        assertEquals(LocalDate.of(2026, 7, 1), m.range.start)
    }
}
