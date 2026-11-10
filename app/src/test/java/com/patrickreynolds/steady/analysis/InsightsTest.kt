package com.patrickreynolds.steady.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val day0 = LocalDate.of(2026, 9, 1)

    private fun days(crashes: Set<Int>, hrvBefore: Double, lag: Int) = (0 until 70).map { i ->
        val wobble = listOf(-0.1, 0.0, 0.1)[i % 3]
        val hrv = if (crashes.any { it - lag == i }) hrvBefore else 4.0 + wobble
        DayRecord(day0.plusDays(i.toLong()), lnRmssd = hrv, rating = if (i in crashes) Rating.CRASH else Rating.OKAY)
    }

    @Test fun clearDipTwoDaysBefore() {
        val s = Insights.describe(Patterns.leadUp(days(setOf(20, 40, 60), 3.4, 2), Metric.HRV))
        assertEquals("Your HRV was clearly lower than usual 2 days before a crash.", s)
    }

    @Test fun noDifference() {
        val s = Insights.describe(Patterns.leadUp(days(setOf(20, 40, 60), 4.0, 1), Metric.HRV))!!
        assertTrue(s, s.contains("about normal"))
    }

    @Test fun oneCrashIsNotEnough() {
        assertNull(Insights.describe(Patterns.leadUp(days(setOf(30), 3.0, 1), Metric.HRV)))
    }
}
