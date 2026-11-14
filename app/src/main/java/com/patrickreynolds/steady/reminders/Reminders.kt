package com.patrickreynolds.steady.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.patrickreynolds.steady.MainActivity
import com.patrickreynolds.steady.R
import com.patrickreynolds.steady.analysis.Rating
import com.patrickreynolds.steady.data.Prefs
import com.patrickreynolds.steady.data.Store
import com.patrickreynolds.steady.steps.PhoneSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Two optional daily notifications: an evening one with four one-tap rating buttons, and a
 * morning nudge to take the reading. Alarms are inexact (no special permission) and are set
 * again after each one fires and after a restart.
 */
object Reminders {
    const val CHANNEL = "daily"
    private const val EVENING_ID = 1
    private const val MORNING_ID = 2
    const val ACTION_EVENING = "com.patrickreynolds.steady.EVENING"
    const val ACTION_MORNING = "com.patrickreynolds.steady.MORNING"
    const val ACTION_RATE = "com.patrickreynolds.steady.RATE"
    /** At midnight, noon and 4 pm the widget's lines change by themselves (see Today), so it is redrawn then. */
    const val ACTION_TICK = "com.patrickreynolds.steady.TICK"
    private val TICK_TIMES = listOf(0 * 60 + 2, 12 * 60, 16 * 60)
    const val EXTRA_RATING = "rating"
    const val EXTRA_DATE = "date"
    const val EXTRA_START_READING = "startReading"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Daily check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The evening rating and the morning reading reminder"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Sets (or clears) both alarms from the current settings. */
    fun schedule(context: Context) {
        val s = Prefs.get(context).current
        set(context, ACTION_EVENING, s.eveningReminder, s.eveningMinutes)
        set(context, ACTION_MORNING, s.morningReminder, s.morningMinutes)
        TICK_TIMES.forEach { set(context, ACTION_TICK, true, it, code = ACTION_TICK.hashCode() + it) }
    }

    private fun set(context: Context, action: String, on: Boolean, minutes: Int, code: Int = action.hashCode()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (!on) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60_000L, pi)
    }

    fun showEvening(context: Context, date: LocalDate = LocalDate.now()) {
        if (Store.get(context).ratings()[date] != null) return   // already rated
        fun rate(r: Rating) = PendingIntent.getBroadcast(
            context, 100 + r.code,
            Intent(context, RatingReceiver::class.java).setAction(ACTION_RATE)
                .putExtra(EXTRA_RATING, r.code).putExtra(EXTRA_DATE, date.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val eink = Prefs.get(context).current.theme == "eink"
        fun RemoteViews.wire() = apply {
            setOnClickPendingIntent(R.id.rate_good, rate(Rating.GOOD))
            setOnClickPendingIntent(R.id.rate_okay, rate(Rating.OKAY))
            setOnClickPendingIntent(R.id.rate_bad, rate(Rating.BAD))
            setOnClickPendingIntent(R.id.rate_crash, rate(Rating.CRASH))
            if (eink) {
                // White buttons with black borders; Crash has a heavy border and a warning sign.
                listOf(R.id.rate_good, R.id.rate_okay, R.id.rate_bad).forEach {
                    setInt(it, "setBackgroundResource", R.drawable.rate_eink); setTextColor(it, android.graphics.Color.BLACK)
                }
                setInt(R.id.rate_crash, "setBackgroundResource", R.drawable.rate_eink_crash)
                setTextColor(R.id.rate_crash, android.graphics.Color.BLACK)
                setTextViewText(R.id.rate_crash, "⚠ Crash")
            }
        }
        val buttons = RemoteViews(context.packageName, R.layout.notification_rating).wire()
        val big = RemoteViews(context.packageName, R.layout.notification_rating_big).wire()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How was today?")
            .setContentText("Good, Okay, Bad or Crash")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buttons)
            .setCustomBigContentView(big)
            .setContentIntent(openApp(context, false))
            .setAutoCancel(true)
            .build()
        notify(context, EVENING_ID, n)
    }

    fun showMorning(context: Context) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Morning reading")
            .setContentText("Put on your strap and lie still. About 2 minutes.")
            .setContentIntent(openApp(context, true))
            .setAutoCancel(true)
            .build()
        notify(context, MORNING_ID, n)
    }

    fun cancelEvening(context: Context) = NotificationManagerCompat.from(context).cancel(EVENING_ID)
    fun cancelMorning(context: Context) = NotificationManagerCompat.from(context).cancel(MORNING_ID)

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }   // no permission: skip quietly
    }

    private fun openApp(context: Context, startReading: Boolean): PendingIntent = PendingIntent.getActivity(
        context, if (startReading) 11 else 10,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_START_READING, startReading)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_EVENING -> Reminders.showEvening(context)
            Reminders.ACTION_MORNING -> {
                val today = LocalDate.now()
                if (Store.get(context).readings().none { it.date == today }) Reminders.showMorning(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> PhoneSteps.schedule(context)
            Reminders.ACTION_TICK -> Unit
        }
        Reminders.schedule(context)
        com.patrickreynolds.steady.Status.refresh(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { PhoneSteps.sample(context) } finally { pending.finish() }
        }
    }
}

class RatingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rating = Rating.of(intent.getIntExtra(Reminders.EXTRA_RATING, 0)) ?: return
        val date = intent.getStringExtra(Reminders.EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        Store.get(context).setRating(date, rating)
        Reminders.cancelEvening(context)
        android.widget.Toast.makeText(context, "Saved: ${rating.label}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
