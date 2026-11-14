package com.patrickreynolds.steady

import com.patrickreynolds.steady.Today.Kind
import com.patrickreynolds.steady.analysis.Rating
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val yesterday = today.minusDays(1)

    private fun texts(hour: Int, done: Boolean, ratings: Map<LocalDate, Rating>, checkIn: CheckIns.CheckIn? = null, update: String? = null) =
        Today.lines(today, hour, done, ratings, hasAnyData = true, checkIn = checkIn, updateVersion = update).map { it.kind to it.text }

    @Test fun morningBeforeAnything() {
        assertEquals(
            listOf(Kind.TODO to "○ Morning reading", Kind.INFO to "Rate today this evening"),
            texts(7, false, mapOf(yesterday to Rating.OKAY)),
        )
    }

    @Test fun missedYesterdayIsAskedInTheMorning() {
        assertEquals(Kind.TODO to "○ Rate yesterday", texts(9, true, emptyMap())[1])
    }

    @Test fun eveningAsksAboutToday() {
        assertEquals(Kind.TODO to "○ Rate today", texts(20, true, mapOf(yesterday to Rating.GOOD))[1])
    }

    @Test fun afterNoonAMissingReadingIsNotATodo() {
        assertEquals(Kind.INFO, Today.lines(today, 13, false, emptyMap(), true, null, null)[0].kind)
    }

    @Test fun ratedTodayShowsTheRating() {
        assertEquals(Kind.DONE to "✓ Today: Crash", texts(21, true, mapOf(today to Rating.CRASH))[1])
    }

    @Test fun checkInBeforeUpdate() {
        val c = CheckIns.ALL[0]
        assertEquals(Kind.ALERT to "! Check-in 1 is due", texts(9, true, emptyMap(), c, "0.4.0")[2])
        assertEquals(Kind.ALERT to "! Update ready: 0.4.0", texts(9, true, emptyMap(), null, "0.4.0")[2])
        assertEquals(2, texts(9, true, emptyMap()).size)
    }

    @Test fun noDataNoYesterdayQuestion() {
        assertEquals(null, Today.askDate(today, 9, emptyMap(), hasAnyData = false))
    }
}
