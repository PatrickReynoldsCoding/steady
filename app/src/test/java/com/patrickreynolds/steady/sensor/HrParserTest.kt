package com.patrickreynolds.steady.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrParserTest {
    @Test fun eightBitHrWithTwoRr() {
        // flags 0x16: 8-bit HR, contact detected, RR present. HR 62, RR 1024 and 512 (1/1024 s).
        val p = HrParser.parse(byteArrayOf(0x16, 62, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(62, p.hr)
        assertEquals(listOf(1000.0, 500.0), p.rrMs)
        assertEquals(true, p.contact)
    }

    @Test fun sixteenBitHrWithEnergyAndRr() {
        // flags 0x19: 16-bit HR, energy expended present, RR present.
        val p = HrParser.parse(byteArrayOf(0x19, 0x2C, 0x01, 0x10, 0x00, 0x00, 0x03))!!
        assertEquals(300, p.hr)
        assertEquals(listOf(750.0), p.rrMs)
        assertNull(p.contact)
    }

    @Test fun noContact() {
        val p = HrParser.parse(byteArrayOf(0x04, 0))!!
        assertEquals(false, p.contact)
        assertEquals(emptyList<Double>(), p.rrMs)
    }

    @Test fun tooShort() {
        assertNull(HrParser.parse(byteArrayOf(0x01, 0x10)))
        assertNull(HrParser.parse(byteArrayOf(0x00)))
    }
}
