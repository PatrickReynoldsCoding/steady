package com.patrickreynolds.steady

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrickreynolds.steady.reminders.Reminders
import com.patrickreynolds.steady.steps.PhoneSteps
import com.patrickreynolds.steady.ui.HomeScreen
import com.patrickreynolds.steady.ui.ReadingScreen
import com.patrickreynolds.steady.ui.SensorPickerScreen
import com.patrickreynolds.steady.ui.SettingsScreen
import com.patrickreynolds.steady.ui.SteadyTheme
import com.patrickreynolds.steady.ui.TrendsScreen
import kotlinx.coroutines.launch

enum class Screen { HOME, READING, TRENDS, SETTINGS, PICK_SENSOR }

class MainActivity : ComponentActivity() {
    /** Set when the morning notification is tapped: go straight to the reading. */
    private val startReading: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { SteadyTheme { SteadyApp(startReading) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { PhoneSteps.sample(this@MainActivity) }
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(Reminders.EXTRA_START_READING, false) == true) {
            startReading.value = true
            Reminders.cancelMorning(this)
            intent.removeExtra(Reminders.EXTRA_START_READING)
        }
    }
}

@Composable
private fun SteadyApp(startReading: MutableState<Boolean>) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val session: ReadingSession = viewModel()

    // Ask once for notifications (only needed on Android 13 and newer).
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun beginReading() {
        screen = Screen.READING
        session.start()
    }
    LaunchedEffect(startReading.value) {
        if (startReading.value) { startReading.value = false; beginReading() }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        if (screen == Screen.READING) session.cancel()
        screen = if (screen == Screen.PICK_SENSOR) Screen.SETTINGS else Screen.HOME
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onStartReading = ::beginReading,
                onTrends = { screen = Screen.TRENDS },
                onSettings = { screen = Screen.SETTINGS },
                onPickSensor = { screen = Screen.PICK_SENSOR },
            )
            Screen.READING -> ReadingScreen(session, onClose = { session.cancel(); screen = Screen.HOME }, onRetry = { session.start() })
            Screen.TRENDS -> TrendsScreen(onBack = { screen = Screen.HOME })
            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME }, onPickSensor = { screen = Screen.PICK_SENSOR })
            Screen.PICK_SENSOR -> SensorPickerScreen(onDone = { screen = Screen.HOME }, onBack = { screen = Screen.SETTINGS })
        }
    }
}
