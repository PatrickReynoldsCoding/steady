package com.patrickreynolds.steady.analysis

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/** The one-tap rating of a day. [code] is what is stored. */
enum class Rating(val code: Int, val label: String) {
    GOOD(1, "Good"), OKAY(2, "Okay"), BAD(3, "Bad"), CRASH(4, "Crash");

    companion object {
        fun of(code: Int?): Rating? = entries.firstOrNull { it.code == code }
    }
}

/** Everything known about one calendar day. Any part can be missing. */
data class DayRecord(
    val date: LocalDate,
    val rmssdMs: Double? = null,
    val lnRmssd: Double? = null,
    val restingHr: Double? = null,
    val rise: Double? = null,
    val steps: Long? = null,
    val rating: Rating? = null,
)

/** The signals the app compares against your own normal. [higherIsWorse] sets the sign of "worse". */
enum class Metric(val label: String, val higherIsWorse: Boolean, val get: (DayRecord) -> Double?) {
    HRV("HRV", false, { it.lnRmssd }),
    RESTING_HR("Resting heart rate", true, { it.restingHr }),
    RISE("Rise on standing", true, { it.rise }),
    STEPS("Steps", false, { it.steps?.toDouble() }),
}

/** Your normal for one signal: the median, and a robust spread (1.4826 × the median absolute deviation, which matches the SD for normal data). */
data class Baseline(val median: Double, val spread: Double, val n: Int) {
    fun z(value: Double): Double = (value - median) / spread
    val low get() = median - spread
    val high get() = median + spread
}

object Patterns {
    const val BASELINE_DAYS = 60L      // look back this far for your normal
    const val MIN_BASELINE_VALUES = 7  // fewer than this: no baseline yet
    const val READINGS_FOR_NORMAL = 14 // the app calls your normal "ready" after this many readings
    val LAGS = 1..3                    // days before a crash start that are examined

    fun baseline(values: List<Double>): Baseline? {
        if (values.size < MIN_BASELINE_VALUES) return null
        val med = median(values)
        val mad = median(values.map { abs(it - med) })
        // A floor so a run of identical values cannot divide by zero.
        val spread = max(1.4826 * mad, max(1e-6, 0.02 * abs(med)))
        return Baseline(med, spread, values.size)
    }

    /**
     * Your normal for [metric] as it stood before [date]: the previous 60 days, with crash days left
     * out so a long crash does not become the new normal.
     */
    fun baselineBefore(days: List<DayRecord>, date: LocalDate, metric: Metric): Baseline? =
        baseline(
            days.filter {
                it.date < date && it.date >= date.minusDays(BASELINE_DAYS) && it.rating != Rating.CRASH
            }.mapNotNull(metric.get)
        )

    /** The latest baseline, including today's value. */
    fun currentBaseline(days: List<DayRecord>, metric: Metric, today: LocalDate): Baseline? =
        baselineBefore(days, today.plusDays(1), metric)

    /** How far each day was from the normal that came before it, in spreads. Null where not known. */
    fun zScores(days: List<DayRecord>, metric: Metric): Map<LocalDate, Double> =
        days.mapNotNull { d ->
            val v = metric.get(d) ?: return@mapNotNull null
            val b = baselineBefore(days, d.date, metric) ?: return@mapNotNull null
            d.date to b.z(v)
        }.toMap()

    /** First day of each crash: a crash day whose two days before were not crash days. */
    fun crashStarts(days: List<DayRecord>): List<LocalDate> {
        val crash = days.filter { it.rating == Rating.CRASH }.map { it.date }.toSet()
        return crash.filter { it.minusDays(1) !in crash && it.minusDays(2) !in crash }.sorted()
    }

    data class LagSummary(val lag: Int, val meanZ: Double?, val n: Int)
    data class LeadUp(
        val metric: Metric,
        val crashStarts: Int,
        val lags: List<LagSummary>,
        val otherMeanZ: Double?,
        val otherN: Int,
    ) {
        /** The lag where this signal looked most "worse than normal", if any lag has data. */
        fun strongestLag(): LagSummary? = lags.filter { it.meanZ != null }
            .maxByOrNull { worse(it.meanZ!!) }

        /** Positive means worse than normal, whatever the direction of the signal. */
        fun worse(z: Double) = if (metric.higherIsWorse) z else -z
    }

    /**
     * Phase 1's main question: did this signal look different in the days before your crashes?
     * Compares the mean z-score 1, 2 and 3 days before each crash start with the mean on ordinary
     * days (not a crash, not in the 3 days before a crash start).
     */
    fun leadUp(days: List<DayRecord>, metric: Metric): LeadUp {
        val z = zScores(days, metric)
        val starts = crashStarts(days)
        val lags = LAGS.map { lag ->
            val vals = starts.mapNotNull { z[it.minusDays(lag.toLong())] }
            LagSummary(lag, vals.takeIf { it.isNotEmpty() }?.average(), vals.size)
        }
        val leadUpDays = starts.flatMap { s -> LAGS.map { s.minusDays(it.toLong()) } }.toSet()
        val crashDays = days.filter { it.rating == Rating.CRASH }.map { it.date }.toSet()
        val other = z.filterKeys { it !in leadUpDays && it !in crashDays }.values
        return LeadUp(metric, starts.size, lags, other.takeIf { it.isNotEmpty() }?.average(), other.size)
    }
}
