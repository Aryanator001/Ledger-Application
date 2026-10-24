package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ledger.query.core.TimeExpressionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Exercises the D8-desugared code on devices without platform java.time. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24, maxSdkVersion = 25)
class LegacyDateApiTest {
    @Test fun localCalendarQueriesWorkOnAndroid7() {
        val today = LocalDate.of(2026, 7, 16)
        val range = TimeExpressionParser.parse(listOf("this", "month"), today)
        assertNotNull(range)
        assertEquals(LocalDate.of(2026, 7, 1), range!!.range.start)
        assertEquals(1784140200000L, today.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli())
    }
}
