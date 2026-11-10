package com.patrickreynolds.steady

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.patrickreynolds.steady.data.Store
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Writes three CSV files and opens the share sheet: one row per day, per reading, and raw beats. */
object Export {
    fun share(context: Context) {
        val store = Store.get(context)
        val dir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        fun n(v: Double?, digits: Int = 2) = v?.let { "%.${digits}f".format(Locale.ROOT, it) } ?: ""

        val days = File(dir, "steady-days.csv")
        days.printWriter().use { w ->
            w.println("date,rmssd_ms,ln_rmssd,resting_hr,rise_on_standing,steps,rating")
            store.days(LocalDate.now()).forEach {
                w.println(listOf(it.date, n(it.rmssdMs, 1), n(it.lnRmssd, 3), n(it.restingHr, 1), n(it.rise, 1),
                    it.steps ?: "", it.rating?.label ?: "").joinToString(","))
            }
        }
        val readings = File(dir, "steady-readings.csv")
        readings.printWriter().use { w ->
            w.println("id,date,started_at,rmssd_ms,ln_rmssd,resting_hr,standing_hr,peak_standing_hr,rise,lying_beats,artifact_pct,quality")
            store.readings().forEach { r ->
                val m = r.metrics
                w.println(listOf(r.id, r.date, Instant.ofEpochMilli(r.startedAtMs), n(m.rmssdMs, 1), n(m.lnRmssd, 3),
                    n(m.restingHr, 1), n(m.standingHr, 1), n(m.peakStandingHr, 1), n(m.rise, 1), m.lyingBeats,
                    n(m.artifactPct, 1), m.quality).joinToString(","))
            }
        }
        val beats = File(dir, "steady-beats.csv")
        beats.printWriter().use { w ->
            w.println("reading_id,phase,ms_into_phase,rr_ms")
            store.rawBeats().forEach { (id, encoded) ->
                encoded.split(';').filter { it.isNotBlank() }.forEach { b ->
                    val (p, ms, rr) = b.split(':')
                    val phase = when (p) { "S" -> "settle"; "L" -> "lying"; else -> "standing" }
                    w.println("$id,$phase,$ms,$rr")
                }
            }
        }

        val uris = arrayListOf(days, readings, beats).map {
            FileProvider.getUriForFile(context, "${context.packageName}.files", it)
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export Steady data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
