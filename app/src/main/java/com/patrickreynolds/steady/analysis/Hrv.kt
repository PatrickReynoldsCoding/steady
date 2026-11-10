package com.patrickreynolds.steady.analysis

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The parts of a morning reading, in order. */
enum class Phase { SETTLE, LYING, STANDING }

/** One beat-to-beat (RR) interval, tagged with the phase it arrived in and how far into that phase. */
data class Beat(val phase: Phase, val phaseMs: Long, val rrMs: Double)

enum class Quality { GOOD, FAIR, POOR }

/** What one morning reading produced. Standing values are null if the standing part was skipped or too short. */
data class ReadingMetrics(
    val rmssdMs: Double?,
    val lnRmssd: Double?,
    val restingHr: Double?,
    val standingHr: Double?,
    val peakStandingHr: Double?,
    val rise: Double?,
    val lyingBeats: Int,
    val artifactPct: Double,
    val quality: Quality,
)

object Hrv {
    const val MIN_RR = 300.0    // 200 bpm
    const val MAX_RR = 2000.0   // 30 bpm
    private const val WINDOW = 5          // neighbours each side for the local median
    private const val MAX_JUMP = 0.20     // a beat more than 20% off its neighbours is an artefact
    const val MIN_PAIRS = 20              // fewer successive valid pairs than this: no RMSSD
    const val STANDING_TAIL_MS = 30_000L  // standing HR uses the last 30 s of the standing part

    /**
     * Marks each interval valid or not. Valid means in the physiological range and within 20% of
     * the median of up to 5 in-range neighbours on each side. This catches missed and extra beats,
     * the usual chest-strap artefacts.
     */
    fun validMask(rr: List<Double>): BooleanArray {
        val inRange = BooleanArray(rr.size) { rr[it] in MIN_RR..MAX_RR }
        return BooleanArray(rr.size) { i ->
            if (!inRange[i]) return@BooleanArray false
            val neighbours = (max(0, i - WINDOW)..min(rr.lastIndex, i + WINDOW))
                .filter { it != i && inRange[it] }
                .map { rr[it] }
            if (neighbours.size < 2) true else abs(rr[i] - median(neighbours)) <= MAX_JUMP * median(neighbours)
        }
    }

    /** RMSSD over successive pairs where both beats are valid. Null if too few pairs. */
    fun rmssd(rr: List<Double>, mask: BooleanArray = validMask(rr)): Double? {
        var sum = 0.0
        var n = 0
        for (i in 1 until rr.size) {
            if (mask[i] && mask[i - 1]) {
                val d = rr[i] - rr[i - 1]
                sum += d * d
                n++
            }
        }
        return if (n < MIN_PAIRS) null else sqrt(sum / n)
    }

    /** Mean heart rate from the valid intervals. */
    fun meanHr(rr: List<Double>, mask: BooleanArray = validMask(rr)): Double? {
        val valid = rr.filterIndexed { i, _ -> mask[i] }
        return if (valid.isEmpty()) null else 60_000.0 / valid.average()
    }

    /** Highest heart rate over any 5 consecutive valid beats: the peak of the jump on standing. */
    fun peakHr(rr: List<Double>, mask: BooleanArray = validMask(rr)): Double? {
        val valid = rr.filterIndexed { i, _ -> mask[i] }
        if (valid.size < 5) return null
        return valid.windowed(5).maxOf { 60_000.0 / it.average() }
    }

    /** Turns the beats of one reading into its metrics. The settle phase is thrown away. */
    fun analyse(beats: List<Beat>): ReadingMetrics {
        val lying = beats.filter { it.phase == Phase.LYING }.map { it.rrMs }
        val lyingMask = validMask(lying)
        val rmssd = rmssd(lying, lyingMask)
        val restingHr = meanHr(lying, lyingMask)

        val standingAll = beats.filter { it.phase == Phase.STANDING }
        val standingEnd = standingAll.maxOfOrNull { it.phaseMs } ?: 0L
        val tail = standingAll.filter { it.phaseMs >= standingEnd - STANDING_TAIL_MS }.map { it.rrMs }
        val standingHr = if (standingEnd >= 45_000L && tail.size >= 15) meanHr(tail) else null
        val peak = if (standingHr != null) peakHr(standingAll.map { it.rrMs }) else null

        val artifacts = lying.size - lyingMask.count { it }
        val artifactPct = if (lying.isEmpty()) 100.0 else 100.0 * artifacts / lying.size
        val quality = when {
            rmssd == null || artifactPct > 20.0 -> Quality.POOR
            artifactPct > 5.0 -> Quality.FAIR
            else -> Quality.GOOD
        }
        return ReadingMetrics(
            rmssdMs = rmssd,
            lnRmssd = rmssd?.let { ln(it) },
            restingHr = restingHr,
            standingHr = standingHr,
            peakStandingHr = peak,
            rise = if (standingHr != null && restingHr != null) standingHr - restingHr else null,
            lyingBeats = lying.size,
            artifactPct = artifactPct,
            quality = quality,
        )
    }
}

fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val s = values.sorted()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
}
