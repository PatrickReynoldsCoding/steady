package com.patrickreynolds.steady

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CheckInsTest {
    private val first = LocalDate.of(2026, 11, 15)

    @Test fun nothingBeforeThreeMornings() {
        assertNull(CheckIns.due(2, first, first.plusDays(2), emptySet()))
    }

    @Test fun firstAfterThreeMornings() {
        assertEquals(1, CheckIns.due(3, first, first.plusDays(2), emptySet())!!.number)
    }

    @Test fun doneOnesAreSkipped() {
        assertNull(CheckIns.due(5, first, first.plusDays(5), setOf(1)))
        assertEquals(2, CheckIns.due(14, first, first.plusDays(16), setOf(1))!!.number)
    }

    @Test fun earliestUndoneComesFirst() {
        assertEquals(1, CheckIns.due(14, first, first.plusDays(30), emptySet())!!.number)
    }

    @Test fun fourWeeksEvenWithFewReadings() {
        assertNull(CheckIns.due(6, first, first.plusDays(27), setOf(1)))
        assertEquals(3, CheckIns.due(6, first, first.plusDays(28), setOf(1))!!.number)
    }
}
