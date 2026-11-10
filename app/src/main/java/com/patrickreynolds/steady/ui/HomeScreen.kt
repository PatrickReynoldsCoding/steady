package com.patrickreynolds.steady.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.patrickreynolds.steady.analysis.Insights
import com.patrickreynolds.steady.analysis.Patterns
import com.patrickreynolds.steady.analysis.Quality
import com.patrickreynolds.steady.analysis.Rating
import com.patrickreynolds.steady.data.Prefs
import com.patrickreynolds.steady.data.Store
import com.patrickreynolds.steady.reminders.Reminders
import com.patrickreynolds.steady.steps.PhoneSteps
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onStartReading: () -> Unit, onTrends: () -> Unit, onSettings: () -> Unit, onPickSensor: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val prefs = remember { Prefs.get(context) }
    val version by store.version.collectAsState()
    val settings by prefs.settings.collectAsState()
    val today = LocalDate.now()
    val days = remember(version, today) { store.days(today) }
    val readings = remember(version) { store.readings() }
    val todaysReading = readings.lastOrNull { it.date == today }
    val goodMornings = readings.filter { it.metrics.quality != Quality.POOR }.map { it.date }.toSet().size
    val ratings = remember(version) { store.ratings() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Steady", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.headlineSmall)
        }

        StatusPanel(goodMornings)

        // Morning reading
        Panel {
            val hasSensor = settings.sensorAddress != null || settings.demoSensor
            when {
                todaysReading != null && todaysReading.metrics.quality != Quality.POOR -> {
                    val m = todaysReading.metrics
                    Text("Morning reading done", style = MaterialTheme.typography.titleLarge)
                    Text(
                        buildString {
                            append("Resting heart rate ${fmt(m.restingHr)}\nHRV ${fmt(m.rmssdMs)} ms")
                            if (m.rise != null) append("\nRise on standing +${fmt(m.rise)}")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = onStartReading) { Text("Take it again") }
                }
                !hasSensor -> {
                    Text("Set up your strap", style = MaterialTheme.typography.titleLarge)
                    Text("Put on your Polar H10 so Steady can find it.", style = MaterialTheme.typography.bodyLarge)
                    BigButton("Find my strap", onPickSensor)
                }
                else -> {
                    Text("Morning reading", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (todaysReading != null) "This morning's reading was too noisy. Wet the electrodes and try again."
                        else "Wet the strap, put it on, and lie down. About 2 minutes. Steady beeps when to stand and when it's done.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BigButton("Start morning reading", onStartReading)
                }
            }
        }

        RatingPanel(today, ratings, hasAnyData = days.isNotEmpty() || todaysReading != null) { date, r ->
            store.setRating(date, r)
            if (date == today) Reminders.cancelEvening(context)
        }

        StepsPanel(asked = settings.stepsAsked) { prefs.update { it.copy(stepsAsked = true) } }

        InsightPanel(days, goodMornings)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onTrends, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Trends", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusPanel(goodMornings: Int) {
    val need = Patterns.READINGS_FOR_NORMAL
    Panel(color = MaterialTheme.colorScheme.primaryContainer) {
        when {
            goodMornings == 0 -> {
                Text("Welcome", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Each morning, take a 2-minute reading with your strap. Each evening, rate your day with one tap. " +
                        "After $need mornings, Steady knows your normal.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            goodMornings < need -> {
                Text("Learning your normal", style = MaterialTheme.typography.titleLarge)
                Text("Morning $goodMornings of $need.", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(
                    progress = { goodMornings.toFloat() / need },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            else -> {
                Text("Your normal is ready", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Keep taking readings and rating your days. Each crash you log teaches Steady what comes before one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Asks about today from 4 pm. Before that, asks about yesterday if it was missed.
 * Shows today's answer, with a way to change it.
 */
@Composable
private fun RatingPanel(today: LocalDate, ratings: Map<LocalDate, Rating>, hasAnyData: Boolean, onRate: (LocalDate, Rating) -> Unit) {
    val yesterday = today.minusDays(1)
    var changing by remember { mutableStateOf(false) }
    val now = LocalTime.now()
    val todayRating = ratings[today]
    val askDate = when {
        changing -> today
        todayRating == null && now.hour >= 16 -> today
        ratings[yesterday] == null && hasAnyData && now.hour < 16 -> yesterday
        else -> null
    }
    if (askDate != null) {
        Panel {
            Text(if (askDate == today) "How was today?" else "How was yesterday?", style = MaterialTheme.typography.titleLarge)
            RatingButtons { r -> onRate(askDate, r); changing = false }
        }
    } else if (todayRating != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today: ${todayRating.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = { changing = true }) { Text("Change") }
        }
    }
}

@Composable
private fun StepsPanel(asked: Boolean, onAsked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(PhoneSteps.permitted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        onAsked()
        if (ok) {
            PhoneSteps.schedule(context)
            scope.launch { PhoneSteps.sample(context) }
        }
    }
    if (granted || asked || !PhoneSteps.available(context)) return
    Panel {
        Text("Count your steps?", style = MaterialTheme.typography.titleLarge)
        Text("Steady can use your phone's step counter. Nothing else to wear or do.", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Allow", { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, Modifier.weight(1f))
            OutlinedButton(onClick = onAsked, modifier = Modifier.weight(1f).heightIn(min = 60.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Not now", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InsightPanel(days: List<com.patrickreynolds.steady.analysis.DayRecord>, goodMornings: Int) {
    val crashes = remember(days) { Patterns.crashStarts(days).size }
    val lines = remember(days) { Insights.all(days) }
    if (goodMornings < 3 && crashes == 0) return
    Panel {
        Text("Before a crash", style = MaterialTheme.typography.titleLarge)
        if (lines.isEmpty()) {
            Text(
                when (crashes) {
                    0 -> "No crashes logged yet. When one happens, tap Crash. After two, Steady shows what your body did in the days before."
                    1 -> "One crash logged. After one more, Steady shows what your body did in the days before."
                    else -> "Not enough readings near your crashes yet. Keep going."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(
                "Based on $crashes crashes. Early days: this gets more reliable with every crash you log.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalStateColors.current.muted,
            )
        }
    }
}
