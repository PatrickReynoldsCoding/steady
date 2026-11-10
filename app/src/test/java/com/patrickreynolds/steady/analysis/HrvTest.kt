package com.patrickreynolds.steady.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.sqrt

class HrvTest {
    /** Alternating 1000/1040 ms: every successive difference is 40 ms, so RMSSD is exactly 40. */
    private fun alternating(n: Int, a: Double = 1000.0, b: Double = 1040.0) = List(n) { if (it % 2 == 0) a else b }

    @Test fun rmssdOfAlternatingIsTheStep() {
        assertEquals(40.0, Hrv.rmssd(alternating(60))!!, 1e-9)
    }

    @Test fun missedBeatIsRejectedAndDoesNotInflateRmssd() {
        val rr = alternating(60).toMutableList()
        rr[30] = 2040.0      // a missed beat: two intervals counted as one
        val mask = Hrv.validMask(rr)
        assertTrue(!mask[30])
        assertEquals(59, mask.count { it })
        assertEquals(40.0, Hrv.rmssd(rr, mask)!!, 1e-9)
    }

    @Test fun outOfRangeIsRejected() {
        val rr = alternating(40).toMutableList()
        rr[5] = 150.0
        rr[6] = 2500.0
        val mask = Hrv.validMask(rr)
        assertTrue(!mask[5] && !mask[6])
    }

    @Test fun tooFewPairsGivesNoRmssd() {
        assertNull(Hrv.rmssd(alternating(15)))
    }

    @Test fun meanHrFromRr() {
        assertEquals(60.0, Hrv.meanHr(List(30) { 1000.0 })!!, 1e-9)
    }

    @Test fun analyseFullReading() {
        val beats = mutableListOf<Beat>()
        var t = 0L
        repeat(20) { beats += Beat(Phase.SETTLE, t, 500.0); t += 500 }          // ignored
        t = 0
        alternating(60).forEach { beats += Beat(Phase.LYING, t, it); t += it.toLong() }
        t = 0
        repeat(80) { beats += Beat(Phase.STANDING, t, 750.0); t += 750 }       // 80 bpm, 60 s
        val m = Hrv.analyse(beats)
        assertEquals(40.0, m.rmssdMs!!, 1e-9)
        assertEquals(ln(40.0), m.lnRmssd!!, 1e-9)
        assertEquals(60_000.0 / 1020.0, m.restingHr!!, 1e-9)
        assertEquals(80.0, m.standingHr!!, 1e-9)
        assertEquals(80.0 - 60_000.0 / 1020.0, m.rise!!, 1e-9)
        assertEquals(Quality.GOOD, m.quality)
        assertEquals(60, m.lyingBeats)
    }

    @Test fun shortStandingGivesNoRise() {
        val beats = alternating(60).mapIndexed { i, rr -> Beat(Phase.LYING, i * 1000L, rr) } +
            List(20) { Beat(Phase.STANDING, it * 750L, 750.0) }
        val m = Hrv.analyse(beats)
        assertNotNull(m.restingHr)
        assertNull(m.rise)
    }

    @Test fun noisyReadingIsPoor() {
        val rr = List(60) { if (it % 3 == 0) 400.0 else 1000.0 }
        val m = Hrv.analyse(rr.mapIndexed { i, v -> Beat(Phase.LYING, i * 1000L, v) })
        assertEquals(Quality.POOR, m.quality)
    }

    @Test fun medianEvenAndOdd() {
        assertEquals(2.0, median(listOf(3.0, 1.0, 2.0)), 0.0)
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0)), 0.0)
        assertEquals(sqrt(4.0), 2.0, 0.0)
    }
}
