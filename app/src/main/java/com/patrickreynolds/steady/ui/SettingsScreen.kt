package com.patrickreynolds.steady.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.patrickreynolds.steady.BuildConfig
import com.patrickreynolds.steady.Export
import com.patrickreynolds.steady.data.Prefs
import com.patrickreynolds.steady.reminders.Reminders
import com.patrickreynolds.steady.sensor.FoundSensor
import com.patrickreynolds.steady.sensor.scanForSensors
import com.patrickreynolds.steady.steps.PhoneSteps
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onPickSensor: () -> Unit, onGuide: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val s by prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var stepsOk by remember { mutableStateOf(PhoneSteps.permitted(context)) }
    val stepsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        stepsOk = ok
        if (ok) { PhoneSteps.schedule(context); scope.launch { PhoneSteps.sample(context) } }
    }

    fun pickTime(minutes: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
    }
    fun reminders(change: (com.patrickreynolds.steady.data.Settings) -> com.patrickreynolds.steady.data.Settings) {
        prefs.update(change)
        Reminders.schedule(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Done") }
        }

        BigButton("Your plan: what to do and why", onGuide)

        Section("Strap") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.sensorName ?: "None chosen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onPickSensor) { Text(if (s.sensorAddress == null) "Find" else "Change") }
            }
            Toggle("Demo strap", "Pretend readings, for trying the app without a strap.", s.demoSensor) { v -> prefs.update { it.copy(demoSensor = v) } }
            Toggle("Stand for the last minute", "Measures how much your heart rate rises when you stand. Turn off on days you can't.", s.includeStanding) { v ->
                prefs.update { it.copy(includeStanding = v) }
            }
        }

        Section("Reminders") {
            Toggle("Evening rating", "A notification with four buttons: Good, Okay, Bad, Crash.", s.eveningReminder) { v -> reminders { it.copy(eveningReminder = v) } }
            if (s.eveningReminder) TimeRow(s.eveningMinutes) { pickTime(s.eveningMinutes) { m -> reminders { it.copy(eveningMinutes = m) } } }
            Toggle("Morning reading", "A nudge to take your reading, if you haven't yet.", s.morningReminder) { v -> reminders { it.copy(morningReminder = v) } }
            if (s.morningReminder) TimeRow(s.morningMinutes) { pickTime(s.morningMinutes) { m -> reminders { it.copy(morningMinutes = m) } } }
            TextButton(onClick = { Reminders.showEvening(context) }) { Text("Show the evening rating now") }
        }

        Section("Steps") {
            when {
                !PhoneSteps.available(context) -> Text("This phone has no step counter.", style = MaterialTheme.typography.bodyLarge)
                stepsOk -> Text("Counting steps with your phone.", style = MaterialTheme.typography.bodyLarge)
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Step counting is off.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { stepsLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Turn on") }
                }
            }
        }

        Section("Your data") {
            Text("Everything stays on this phone. Export gives you three CSV files: days, readings and raw beats.", style = MaterialTheme.typography.bodyMedium)
            BigButton("Export data", { Export.share(context) })
        }

        UpdatesSection()

        Text(
            "Steady ${BuildConfig.VERSION_NAME}. A personal tool for pacing, not a medical device. " +
                "Phase 1 learns your normal and shows what comes before your crashes. It doesn't give warnings yet.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalStateColors.current.muted,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Toggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalStateColors.current.muted)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeRow(minutes: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("At %02d:%02d".format(minutes / 60, minutes % 60), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) { Text("Change time") }
    }
}

/** Finds nearby straps. The H10 only shows up while it is worn with wet electrodes. */
@Composable
fun SensorPickerScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val perms = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    fun granted() = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = granted(); asked = true }
    var found by remember { mutableStateOf(emptyList<FoundSensor>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!ok) launcher.launch(perms) }
    LaunchedEffect(ok) {
        if (ok) scanForSensors(context).catch { error = it.message }.collect { found = it }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Find your strap", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Cancel") }
        }
        Text("Wet the electrodes and put the strap on. It only shows up while you're wearing it.", style = MaterialTheme.typography.bodyLarge)
        when {
            !ok -> {
                Text(if (asked) "Steady needs Nearby devices permission to find the strap." else "Allow Nearby devices to continue.", style = MaterialTheme.typography.bodyLarge)
                BigButton("Allow", { launcher.launch(perms) })
            }
            error != null -> Text(error!!, style = MaterialTheme.typography.bodyLarge, color = LocalStateColors.current.stop)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyLarge)
                }
                found.forEach { f ->
                    Panel(Modifier.clickable {
                        prefs.update { it.copy(sensorAddress = f.address, sensorName = f.name, demoSensor = false) }
                        onDone()
                    }) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium)
                        Text("Tap to use this strap", style = MaterialTheme.typography.bodySmall, color = LocalStateColors.current.muted)
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { prefs.update { it.copy(demoSensor = true) }; onDone() }) { Text("No strap? Use the demo strap") }
    }
}
