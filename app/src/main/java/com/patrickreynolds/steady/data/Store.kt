package com.patrickreynolds.steady.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.patrickreynolds.steady.analysis.Beat
import com.patrickreynolds.steady.analysis.DayRecord
import com.patrickreynolds.steady.analysis.Phase
import com.patrickreynolds.steady.analysis.Quality
import com.patrickreynolds.steady.analysis.Rating
import com.patrickreynolds.steady.analysis.ReadingMetrics
import com.patrickreynolds.steady.analysis.StepSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** A saved morning reading. */
data class Reading(
    val id: Long,
    val date: LocalDate,
    val startedAtMs: Long,
    val metrics: ReadingMetrics,
)

/**
 * Everything the app stores, in one SQLite file on the phone. Nothing leaves the phone except
 * through Export. Raw beats are kept so readings can be re-analysed if the maths improves.
 */
class Store private constructor(context: Context) :
    SQLiteOpenHelper(context, "steady.db", null, 1) {

    /** Bumped on every write so screens can reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE readings(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                rmssd REAL, ln_rmssd REAL, resting_hr REAL,
                standing_hr REAL, peak_standing_hr REAL, rise REAL,
                lying_beats INTEGER NOT NULL, artifact_pct REAL NOT NULL, quality TEXT NOT NULL,
                beats TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX readings_date ON readings(date)")
        db.execSQL("CREATE TABLE ratings(date TEXT PRIMARY KEY, rating INTEGER NOT NULL, rated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE steps(date TEXT PRIMARY KEY, steps INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE step_last(id INTEGER PRIMARY KEY CHECK (id = 0), at_ms INTEGER NOT NULL, count INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun changed() { _version.value++ }

    // ---- readings ----

    fun saveReading(date: LocalDate, startedAtMs: Long, m: ReadingMetrics, beats: List<Beat>): Long {
        val v = ContentValues().apply {
            put("date", date.toString())
            put("started_at", startedAtMs)
            put("rmssd", m.rmssdMs); put("ln_rmssd", m.lnRmssd); put("resting_hr", m.restingHr)
            put("standing_hr", m.standingHr); put("peak_standing_hr", m.peakStandingHr); put("rise", m.rise)
            put("lying_beats", m.lyingBeats); put("artifact_pct", m.artifactPct); put("quality", m.quality.name)
            put("beats", encodeBeats(beats))
        }
        return writableDatabase.insert("readings", null, v).also { changed() }
    }

    fun deleteReading(id: Long) {
        writableDatabase.delete("readings", "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun readings(): List<Reading> = readableDatabase.rawQuery(
        "SELECT id, date, started_at, rmssd, ln_rmssd, resting_hr, standing_hr, peak_standing_hr, rise, " +
            "lying_beats, artifact_pct, quality FROM readings ORDER BY started_at", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                fun d(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
                add(
                    Reading(
                        id = c.getLong(0),
                        date = LocalDate.parse(c.getString(1)),
                        startedAtMs = c.getLong(2),
                        metrics = ReadingMetrics(
                            rmssdMs = d(3), lnRmssd = d(4), restingHr = d(5), standingHr = d(6),
                            peakStandingHr = d(7), rise = d(8), lyingBeats = c.getInt(9),
                            artifactPct = c.getDouble(10), quality = Quality.valueOf(c.getString(11)),
                        ),
                    )
                )
            }
        }
    }

    /** Raw beats of every reading, for export: id to beats. */
    fun rawBeats(): List<Pair<Long, String>> =
        readableDatabase.rawQuery("SELECT id, beats FROM readings ORDER BY started_at", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }

    // ---- ratings ----

    fun setRating(date: LocalDate, rating: Rating) {
        val v = ContentValues().apply {
            put("date", date.toString()); put("rating", rating.code); put("rated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("ratings", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    fun ratings(): Map<LocalDate, Rating> =
        readableDatabase.rawQuery("SELECT date, rating FROM ratings", null).use { c ->
            buildMap { while (c.moveToNext()) Rating.of(c.getInt(1))?.let { put(LocalDate.parse(c.getString(0)), it) } }
        }

    // ---- steps ----

    fun lastStepSample(): StepSample? =
        readableDatabase.rawQuery("SELECT at_ms, count FROM step_last WHERE id = 0", null).use { c ->
            if (c.moveToFirst()) StepSample(c.getLong(0), c.getLong(1)) else null
        }

    /** Records a new counter sample and adds [delta] steps to its day, in one transaction. */
    fun addStepSample(sample: StepSample, delta: Pair<LocalDate, Long>?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR REPLACE INTO step_last(id, at_ms, count) VALUES (0, ?, ?)", arrayOf(sample.atMs, sample.count))
            if (delta != null) {
                db.execSQL("INSERT OR IGNORE INTO steps(date, steps) VALUES (?, 0)", arrayOf(delta.first.toString()))
                db.execSQL("UPDATE steps SET steps = steps + ? WHERE date = ?", arrayOf(delta.second, delta.first.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (delta != null) changed()
    }

    fun steps(): Map<LocalDate, Long> =
        readableDatabase.rawQuery("SELECT date, steps FROM steps", null).use { c ->
            buildMap { while (c.moveToNext()) put(LocalDate.parse(c.getString(0)), c.getLong(1)) }
        }

    // ---- days ----

    /**
     * One record per day from the first day with any data to [today]. A day with several readings
     * uses its best one (fewest artefacts, latest on a tie); poor readings are left out.
     */
    fun days(today: LocalDate): List<DayRecord> {
        val readings = readings().filter { it.metrics.quality != Quality.POOR }
            .groupBy { it.date }
            .mapValues { (_, rs) -> rs.minWith(compareBy<Reading> { it.metrics.artifactPct }.thenByDescending { it.startedAtMs }) }
        val ratings = ratings()
        val steps = steps()
        val first = (readings.keys + ratings.keys + steps.keys).minOrNull() ?: return emptyList()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= today }.map { d ->
            val r = readings[d]?.metrics
            DayRecord(d, r?.rmssdMs, r?.lnRmssd, r?.restingHr, r?.rise, steps[d], ratings[d])
        }.toList()
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }

        /** Beats as "phase:ms:rr;..." with phase S, L or T (standing). Compact and easy to re-parse. */
        fun encodeBeats(beats: List<Beat>): String = beats.joinToString(";") {
            val p = when (it.phase) { Phase.SETTLE -> "S"; Phase.LYING -> "L"; Phase.STANDING -> "T" }
            "$p:${it.phaseMs}:${"%.1f".format(java.util.Locale.ROOT, it.rrMs)}"
        }
    }
}
