package com.patrickreynolds.steady

import com.patrickreynolds.steady.analysis.Rating
import java.time.LocalDate

/**
 * What needs doing right now, in a few short lines: the home-screen widget shows these. Pure, so
 * it can be tested; [Status] fills it in from the phone.
 */
object Today {
    enum class Kind { TODO, DONE, INFO, ALERT }
    data class Line(val kind: Kind, val text: String, val target: Target)
    enum class Target { READING, RATING, HOME }

    /** Before this hour the evening question is about yesterday (if it was missed). Matches the home screen. */
    const val RATE_TODAY_FROM_HOUR = 16
    /** After this hour a missing morning reading is no longer a to-do. Missing a day is fine. */
    const val MORNING_UNTIL_HOUR = 12

    /** Which day to ask "how was it?" about, or null. The same rule the home screen uses. */
    fun askDate(today: LocalDate, hour: Int, ratings: Map<LocalDate, Rating>, hasAnyData: Boolean): LocalDate? {
        val yesterday = today.minusDays(1)
        return when {
            ratings[today] == null && hour >= RATE_TODAY_FROM_HOUR -> today
            ratings[yesterday] == null && hasAnyData && hour < RATE_TODAY_FROM_HOUR -> yesterday
            else -> null
        }
    }

    fun lines(
        today: LocalDate,
        hour: Int,
        readingDone: Boolean,
        ratings: Map<LocalDate, Rating>,
        hasAnyData: Boolean,
        checkIn: CheckIns.CheckIn?,
        updateVersion: String?,
    ): List<Line> = buildList {
        add(
            when {
                readingDone -> Line(Kind.DONE, "✓ Morning reading done", Target.HOME)
                hour < MORNING_UNTIL_HOUR -> Line(Kind.TODO, "○ Morning reading", Target.READING)
                else -> Line(Kind.INFO, "No morning reading today. That's fine.", Target.HOME)
            }
        )
        val ask = askDate(today, hour, ratings, hasAnyData)
        val rated = ratings[today]
        add(
            when {
                ask == today -> Line(Kind.TODO, "○ Rate today", Target.RATING)
                ask != null -> Line(Kind.TODO, "○ Rate yesterday", Target.RATING)
                rated != null -> Line(Kind.DONE, "✓ Today: ${rated.label}", Target.HOME)
                else -> Line(Kind.INFO, "Rate today this evening", Target.HOME)
            }
        )
        when {
            checkIn != null -> add(Line(Kind.ALERT, "! Check-in ${checkIn.number} is due", Target.HOME))
            updateVersion != null -> add(Line(Kind.ALERT, "! Update ready: $updateVersion", Target.HOME))
        }
    }
}
