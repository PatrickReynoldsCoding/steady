package com.patrickreynolds.steady

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.patrickreynolds.steady.analysis.Quality
import com.patrickreynolds.steady.data.Prefs
import com.patrickreynolds.steady.data.Store
import com.patrickreynolds.steady.reminders.Reminders
import java.time.LocalDate
import java.time.LocalTime

/**
 * Keeps everything outside the app up to date: the home-screen widget, and the one-off
 * notification when a check-in becomes due. Called whenever data or settings change, when the
 * app opens, and by alarms at midnight, noon and 4 pm (when the widget's lines change by
 * themselves).
 */
object Status {
    private const val CHECK_IN_ID = 4

    fun refresh(context: Context) {
        val app = context.applicationContext
        runCatching { notifyCheckIn(app) }
        runCatching { updateWidgets(app) }
    }

    private data class Facts(
        val goodMornings: Int, val firstReading: LocalDate?, val readingToday: Boolean, val hasAnyData: Boolean,
    )

    private fun facts(context: Context, today: LocalDate): Facts {
        val readings = Store.get(context).readings().filter { it.metrics.quality != Quality.POOR }
        val ratings = Store.get(context).ratings()
        return Facts(
            goodMornings = readings.map { it.date }.toSet().size,
            firstReading = readings.minOfOrNull { it.date },
            readingToday = readings.any { it.date == today },
            hasAnyData = readings.isNotEmpty() || ratings.isNotEmpty(),
        )
    }

    // ---- check-in notification ----

    private fun notifyCheckIn(context: Context) {
        val prefs = Prefs.get(context)
        val s = prefs.current
        val today = LocalDate.now()
        val f = facts(context, today)
        val due = CheckIns.due(f.goodMornings, f.firstReading, today, s.checkInsDone) ?: return
        if (due.number in s.checkInsNotified) return
        val open = PendingIntent.getActivity(
            context, 20, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for a check-in")
            .setContentText(due.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${due.title}\n${due.body}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(CHECK_IN_ID, n) }
        prefs.update { it.copy(checkInsNotified = it.checkInsNotified + due.number) }
    }

    fun cancelCheckInNotification(context: Context) = NotificationManagerCompat.from(context).cancel(CHECK_IN_ID)

    // ---- widget ----

    private fun updateWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, SteadyWidget::class.java))
        if (ids.isEmpty()) return
        mgr.updateAppWidget(ids, views(context))
    }

    private enum class WidgetLook { LIGHT, DARK, EINK }

    private fun look(context: Context): WidgetLook = when (Prefs.get(context).current.theme) {
        "light" -> WidgetLook.LIGHT
        "dark" -> WidgetLook.DARK
        "eink" -> WidgetLook.EINK
        else -> if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) WidgetLook.DARK else WidgetLook.LIGHT
    }

    fun views(context: Context): RemoteViews {
        val today = LocalDate.now()
        val hour = LocalTime.now().hour
        val s = Prefs.get(context).current
        val f = facts(context, today)
        val ratings = Store.get(context).ratings()
        val checkIn = CheckIns.due(f.goodMornings, f.firstReading, today, s.checkInsDone)
        val update = s.updateVersion?.takeIf { Updater.newer(it, BuildConfig.VERSION_NAME) }
        val lines = Today.lines(today, hour, f.readingToday, ratings, f.hasAnyData, checkIn, update)

        val look = look(context)
        val (bg, ink, accent, muted) = when (look) {
            WidgetLook.LIGHT -> listOf(R.drawable.widget_bg_light, 0xFF16202A.toInt(), 0xFF2C5A86.toInt(), 0xFF546270.toInt())
            WidgetLook.DARK -> listOf(R.drawable.widget_bg_dark, 0xFFE2E8EE.toInt(), 0xFF8FB6DE.toInt(), 0xFF9BA8B4.toInt())
            WidgetLook.EINK -> listOf(R.drawable.widget_bg_eink, Color.BLACK, Color.BLACK, Color.BLACK)
        }
        val v = RemoteViews(context.packageName, R.layout.widget_steady)
        v.setInt(R.id.widget_root, "setBackgroundResource", bg)
        v.setTextColor(R.id.widget_title, accent)
        v.setOnClickPendingIntent(R.id.widget_root, open(context, 30, false))
        val rows = listOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
        rows.forEachIndexed { i, id ->
            val line = lines.getOrNull(i)
            if (line == null) { v.setViewVisibility(id, View.GONE); return@forEachIndexed }
            v.setViewVisibility(id, View.VISIBLE)
            // To-dos and alerts are bold: the difference must survive e-ink, where all text is black.
            val bold = line.kind == Today.Kind.TODO || line.kind == Today.Kind.ALERT
            val text = SpannableString(line.text).apply { if (bold) setSpan(StyleSpan(Typeface.BOLD), 0, length, 0) }
            v.setTextViewText(id, text)
            v.setTextColor(id, when (line.kind) { Today.Kind.TODO, Today.Kind.ALERT -> accent; Today.Kind.DONE -> ink; Today.Kind.INFO -> muted })
            v.setOnClickPendingIntent(id, open(context, 31 + i, line.target == Today.Target.READING))
        }
        return v
    }

    private fun open(context: Context, code: Int, startReading: Boolean): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(Reminders.EXTRA_START_READING, startReading)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The home-screen widget. All drawing is in [Status.views]. */
class SteadyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Status.refresh(context)
    }
}
