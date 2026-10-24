package com.ledger.query.exec

import com.example.data.ExecIdListRow
import com.example.data.ExecScalarRow
import com.example.data.Transaction
import com.example.data.TransactionDao
import com.ledger.query.core.Direction
import com.ledger.query.core.Lang
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryPlan
import com.ledger.query.core.TimeRange
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class RoomQueryExecutorTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun saveTimeZone() { originalZone = TimeZone.getDefault() }

    @After
    fun restoreTimeZone() { TimeZone.setDefault(originalZone) }

    @Test
    fun indianCalendarDayUsesLocalMidnightBoundaries() = runBlocking {
        assertDayBounds("Asia/Kolkata", "2026-07-16",
            "2026-07-15T18:30:00Z", "2026-07-16T18:29:59.999Z")
    }

    @Test
    fun springDaylightSavingDayHas23Hours() = runBlocking {
        assertDayBounds("America/New_York", "2026-03-08",
            "2026-03-08T05:00:00Z", "2026-03-09T03:59:59.999Z")
    }

    @Test
    fun autumnDaylightSavingDayHas25Hours() = runBlocking {
        assertDayBounds("America/New_York", "2026-11-01",
            "2026-11-01T04:00:00Z", "2026-11-02T04:59:59.999Z")
    }

    @Test
    fun utcCalendarDayRetainsExistingBoundaries() = runBlocking {
        assertDayBounds("UTC", "2026-07-16",
            "2026-07-16T00:00:00Z", "2026-07-16T23:59:59.999Z")
    }

    @Test
    fun listedTransactionUsesSameLocalDateAsQuery() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val date = LocalDate.of(2026, 7, 16)
        val dao = RecordingDao(listOf(Transaction(
            id = "early", title = "Breakfast", amount = 50.0, categoryId = "food",
            type = "EXPENSE", date = Instant.parse("2026-07-15T19:00:00Z").toEpochMilli()
        )))
        val result = RoomQueryExecutor(dao.instance) { "scope" }.execute(
            ResolvedQueryPlan(QueryPlan(Direction.OUT, Metric.LIST, start = date, end = date), null, null)
        ) as QueryResult.Rows

        assertEquals(date, result.transactions.single().date)
    }

    @Test
    fun analystCurrentAndPriorPeriodsUseLocalCalendarBoundaries() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val date = LocalDate.of(2026, 7, 16)
        val dao = RecordingDao()
        RoomQueryExecutor(dao.instance) { "scope" }.gatherContext(QueryFrame(
            rawQuery = "why expenses today", normalizedQuery = "why expenses today",
            language = Lang.EN, direction = Direction.OUT, metric = Metric.SUM,
            timeRange = TimeRange(date, date, "Today"), paymentMethod = "cash"
        ))

        val prior = Instant.parse("2026-07-14T18:30:00Z").toEpochMilli() to
            Instant.parse("2026-07-15T18:29:59.999Z").toEpochMilli()
        val current = Instant.parse("2026-07-15T18:30:00Z").toEpochMilli() to
            Instant.parse("2026-07-16T18:29:59.999Z").toEpochMilli()
        assertTrue(dao.ranges.contains(prior))
        assertTrue(dao.ranges.contains(current))
        assertTrue(dao.ranges.all { it == prior || it == current })
    }

    private suspend fun assertDayBounds(zone: String, day: String, start: String, end: String) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val date = LocalDate.parse(day)
        val dao = RecordingDao()
        RoomQueryExecutor(dao.instance) { "scope" }.execute(
            ResolvedQueryPlan(QueryPlan(Direction.OUT, Metric.SUM, start = date, end = date), null, null)
        )
        val expected = Instant.parse(start).toEpochMilli() to Instant.parse(end).toEpochMilli()
        assertTrue(dao.ranges.isNotEmpty())
        dao.ranges.forEach { assertEquals(expected, it) }
    }

    /** Records the DAO boundary without Android, a database file, or network calls. */
    private class RecordingDao(private val transactions: List<Transaction> = emptyList()) {
        val ranges = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val instance = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader, arrayOf(TransactionDao::class.java)
        ) { _, method, args ->
            val startIndex = when (method.name) {
                "execScalar", "execNetScalar", "execIdList", "execList" ->
                    if (method.name == "execNetScalar") 3 else 4
                "execGroupByCategory", "execGroupByEntity" -> 3
                else -> error("Unexpected DAO call: ${method.name}")
            }
            ranges += (args[startIndex] as Long) to (args[startIndex + 1] as Long)
            when (method.name) {
                "execList" -> transactions
                "execScalar", "execNetScalar" -> ExecScalarRow(0.0, 0)
                "execIdList" -> ExecIdListRow(null)
                else -> emptyList<Any>()
            }
        } as TransactionDao
    }
}
