package com.patrickreynolds.steady.analysis

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A reading of the phone's step counter, which counts up from 0 at each boot. */
data class StepSample(val atMs: Long, val count: Long)

object Steps {
    /**
     * The steps between two samples and the day they belong to, or null if there is nothing to add.
     * A lower count than last time means the phone restarted, so the new count is all new steps.
     * If the gap crosses midnight the steps go to the earlier day: they are mostly evening steps
     * taken before the phone's background work stopped for the night.
     */
    fun delta(last: StepSample?, next: StepSample, zone: ZoneId): Pair<LocalDate, Long>? {
        if (last == null || next.atMs <= last.atMs) return null
        val steps = if (next.count >= last.count) next.count - last.count else next.count
        if (steps <= 0) return null
        val lastDay = Instant.ofEpochMilli(last.atMs).atZone(zone).toLocalDate()
        return lastDay to steps
    }
}
