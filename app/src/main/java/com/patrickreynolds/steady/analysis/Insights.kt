package com.patrickreynolds.steady.analysis

import kotlin.math.abs

/** Plain sentences about what came before your crashes. */
object Insights {
    const val MIN_CRASHES = 2

    private fun name(m: Metric) = when (m) {
        Metric.HRV -> "Your HRV"
        Metric.RESTING_HR -> "Your resting heart rate"
        Metric.RISE -> "Your heart-rate rise on standing"
        Metric.STEPS -> "Your step count"
    }

    private fun whenWord(lag: Int) = if (lag == 1) "the day before" else "$lag days before"

    /**
     * One sentence for one signal, or null if there is not enough data. Picks the day (1-3 before)
     * that differs most from ordinary days. A difference under half a spread counts as "about normal".
     */
    fun describe(lu: Patterns.LeadUp): String? {
        if (lu.crashStarts < MIN_CRASHES) return null
        val other = lu.otherMeanZ ?: return null
        val best = lu.lags.filter { it.meanZ != null && it.n >= MIN_CRASHES }
            .maxByOrNull { abs(it.meanZ!! - other) } ?: return null
        val diff = best.meanZ!! - other
        val dir = if (diff > 0) "higher" else "lower"
        return when {
            abs(diff) >= 1.0 -> "${name(lu.metric)} was clearly $dir than usual ${whenWord(best.lag)} a crash."
            abs(diff) >= 0.5 -> "${name(lu.metric)} was a little $dir than usual ${whenWord(best.lag)} a crash."
            else -> "${name(lu.metric)} looked about normal in the days before a crash."
        }
    }

    fun all(days: List<DayRecord>): List<String> =
        Metric.entries.mapNotNull { describe(Patterns.leadUp(days, it)) }
}
