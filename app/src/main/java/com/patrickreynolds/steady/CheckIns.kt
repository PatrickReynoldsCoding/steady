package com.patrickreynolds.steady

import java.time.LocalDate

/**
 * The three check-ins from GUIDE.md. Steady shows a card when one is due, so they don't have to
 * be remembered. Kept in step with the guide's "Check-ins" section.
 */
object CheckIns {
    data class CheckIn(val number: Int, val title: String, val body: String)

    const val TRIAL_DAYS = 28L

    val ALL = listOf(
        CheckIn(1, "Check-in 1: is it working?", "You've done 3 mornings. Time to say how it's going: the strap, the reminder, and anything tiring or confusing."),
        CheckIn(2, "Check-in 2: your normal", "Steady has learned your normal. Time to look at your trends together."),
        CheckIn(3, "Check-in 3: what next?", "4 weeks done. Time to decide the next step together."),
    )

    /** The earliest check-in that has been reached and not yet marked done, or null. */
    fun due(goodMornings: Int, firstReading: LocalDate?, today: LocalDate, done: Set<Int>): CheckIn? {
        val reached = buildSet {
            if (goodMornings >= 3) add(1)
            if (goodMornings >= 14) add(2)
            if (firstReading != null && !today.isBefore(firstReading.plusDays(TRIAL_DAYS))) add(3)
        }
        return ALL.firstOrNull { it.number in reached && it.number !in done }
    }
}
