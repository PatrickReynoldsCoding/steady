package com.patrickreynolds.steady.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PatternsTest {
    private val day0 = LocalDate.of(2026, 9, 1)

    @Test fun baselineNeedsSevenValues() {
        assertNull(Patterns.baseline(List(6) { 1.0 }))
        val b = Patterns.baseline(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0))!!
        assertEquals(4.0, b.median, 0.0)
        assertEquals(1.4826 * 2.0, b.spread, 1e-9)
    }

    @Test fun identicalValuesDoNotDivideByZero() {
        val b = Patterns.baseline(List(10) { 4.0 })!!
        assertTrue(b.spread > 0)
    }

    @Test fun baselineSkipsCrashDaysAndFutureDays() {
        val days = (0 until 20).map { i ->
            DayRecord(day0.plusDays(i.toLong()), lnRmssd = if (i == 5) 1.0 else 4.0,
                rating = if (i == 5) Rating.CRASH else Rating.GOOD)
        }
        val b = Patterns.baselineBefore(days, day0.plusDays(10), Metric.HRV)!!
        assertEquals(9, b.n)          // days 0-9, minus the crash day
        assertEquals(4.0, b.median, 0.0)
    }

    @Test fun crashStartsNeedTwoClearDaysBefore() {
        val ratings = mapOf(10 to Rating.CRASH, 11 to Rating.CRASH, 12 to Rating.CRASH, 14 to Rating.CRASH, 20 to Rating.CRASH)
        val days = (0 until 25).map { DayRecord(day0.plusDays(it.toLong()), rating = ratings[it] ?: Rating.OKAY) }
        // 14 follows 12 with one clear day: still the same crash. 10 and 20 are starts.
        assertEquals(listOf(day0.plusDays(10), day0.plusDays(20)), Patterns.crashStarts(days))
    }

    @Test fun leadUpSeesTheDipBeforeCrashes() {
        // Normal HRV 4.0 +/- small wobble; on the day before each crash it drops to 3.5.
        val crashes = setOf(20, 35, 50)
        val days = (0 until 60).map { i ->
            val wobble = listOf(-0.05, 0.0, 0.05)[i % 3]
            val hrv = when {
                i in crashes -> 3.3
                i + 1 in crashes -> 3.5
                else -> 4.0 + wobble
            }
            DayRecord(day0.plusDays(i.toLong()), lnRmssd = hrv, rating = if (i in crashes) Rating.CRASH else Rating.GOOD)
        }
        val lu = Patterns.leadUp(days, Metric.HRV)
        assertEquals(3, lu.crashStarts)
        val lag1 = lu.lags.first { it.lag == 1 }
        assertEquals(3, lag1.n)
        assertTrue("lag-1 HRV should be well below normal: ${lag1.meanZ}", lag1.meanZ!! < -3)
        assertTrue(kotlin.math.abs(lu.otherMeanZ!!) < 0.5)
        assertEquals(1, lu.strongestLag()!!.lag)
    }

    @Test fun leadUpWithNoCrashes() {
        val days = (0 until 20).map { DayRecord(day0.plusDays(it.toLong()), lnRmssd = 4.0) }
        val lu = Patterns.leadUp(days, Metric.HRV)
        assertEquals(0, lu.crashStarts)
        assertNull(lu.strongestLag())
        assertNotNull(lu.otherMeanZ)
    }

    @Test fun stepDeltas() {
        val zone: ZoneId = ZoneOffset.UTC
        val t = day0.atStartOfDay(zone).toInstant().toEpochMilli()
        val h = 3_600_000L
        assertNull(Steps.delta(null, StepSample(t, 100), zone))
        assertEquals(day0 to 50L, Steps.delta(StepSample(t + h, 100), StepSample(t + 2 * h, 150), zone))
        // Reboot: count went down, so the new count is all new steps.
        assertEquals(day0 to 30L, Steps.delta(StepSample(t + h, 5000), StepSample(t + 2 * h, 30), zone))
        // Crosses midnight: goes to the earlier day.
        assertEquals(day0 to 20L, Steps.delta(StepSample(t + 22 * h, 0), StepSample(t + 32 * h, 20), zone))
        assertNull(Steps.delta(StepSample(t + h, 10), StepSample(t + 2 * h, 10), zone))
    }
}
