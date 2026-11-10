package com.patrickreynolds.steady.sensor

/** One Heart Rate Measurement notification: the heart rate and any RR intervals it carried. */
data class HrPacket(val hr: Int, val rrMs: List<Double>, val contact: Boolean?)

/**
 * Parses the standard Bluetooth Heart Rate Measurement characteristic (0x2A37), which the Polar H10
 * and most chest straps send about once a second.
 *
 * Byte 0 is flags: bit 0 heart rate is 16-bit, bits 1-2 sensor contact, bit 3 energy expended
 * present, bit 4 RR intervals present. RR intervals are 16-bit little-endian in 1/1024 s.
 */
object HrParser {
    fun parse(value: ByteArray): HrPacket? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xFF
        var i = 1
        val hr: Int
        if (flags and 0x01 != 0) {
            if (value.size < 3) return null
            hr = u16(value, i); i += 2
        } else {
            hr = value[i].toInt() and 0xFF; i += 1
        }
        val contact = when ((flags shr 1) and 0x03) {
            0x03 -> true
            0x02 -> false
            else -> null   // contact detection not supported
        }
        if (flags and 0x08 != 0) i += 2
        val rr = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            while (i + 1 < value.size) {
                rr += u16(value, i) * 1000.0 / 1024.0
                i += 2
            }
        }
        return HrPacket(hr, rr, contact)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}
