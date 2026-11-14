package com.patrickreynolds.steady.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrickreynolds.steady.ReadingSession
import com.patrickreynolds.steady.ReadingUi
import com.patrickreynolds.steady.analysis.Phase
import com.patrickreynolds.steady.analysis.Quality

@Composable
fun ReadingScreen(session: ReadingSession, onClose: () -> Unit, onRetry: () -> Unit) {
    val state by session.state.collectAsState()
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            ReadingUi.Idle, ReadingUi.Connecting -> {
                Title("Looking for your strap")
                Body("Wet the electrodes, put the strap on, and lie down. Steady starts by itself when it finds it.")
                if (!LocalEink.current) CircularProgressIndicator(Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Secondary("Cancel", onClose)
            }
            is ReadingUi.Measuring -> Measuring(s, onClose)
            is ReadingUi.Done -> {
                val m = s.metrics
                Title(
                    when (m.quality) {
                        Quality.GOOD -> "Done. Thank you."
                        Quality.FAIR -> "Done"
                        Quality.POOR -> "That reading was too noisy"
                    }
                )
                Body(
                    when (m.quality) {
                        Quality.GOOD -> "Your reading is saved."
                        Quality.FAIR -> "A few beats were noisy, but the reading is usable."
                        Quality.POOR -> "Steady won't use it. Wet the electrodes, check the strap is snug, and try again."
                    }
                )
                if (m.quality != Quality.POOR) {
                    Panel {
                        Value("Resting heart rate", "${fmt(m.restingHr)} bpm")
                        Value("HRV (RMSSD)", "${fmt(m.rmssdMs)} ms")
                        if (m.rise != null) Value("Rise on standing", "+${fmt(m.rise)} bpm")
                    }
                }
                if (m.quality == Quality.POOR) BigButton("Try again", onRetry)
                Secondary(if (m.quality == Quality.POOR) "Not now" else "Back to home", onClose)
            }
            is ReadingUi.Failed -> {
                Title("Something went wrong")
                Body(s.message)
                BigButton("Try again", onRetry)
                Secondary("Back to home", onClose)
            }
        }
    }
}

@Composable
private fun Measuring(s: ReadingUi.Measuring, onClose: () -> Unit) {
    val states = LocalStateColors.current
    val (title, body) = when (s.phase) {
        Phase.SETTLE -> "Settle in" to "Lie still and breathe normally. Measuring starts in a moment."
        Phase.LYING -> "Lie still" to (if (s.includeStanding) "Measuring. Steady beeps twice when it's time to stand." else "Measuring. Steady beeps when it's done.")
        Phase.STANDING -> "Stand up now" to "Stand still with your arms by your side. Steady beeps when it's done."
    }
    PhaseSteps(s.phase, s.includeStanding)
    Title(title)
    Body(body)
    val ring = MaterialTheme.colorScheme.primary
    val eink = LocalEink.current
    val track = if (eink) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    // E-ink: the count moves in steps of 5 seconds, so the screen redraws 12 times a minute, not 60.
    val shownLeft = if (eink) ((s.secondsLeft + 4) / 5) * 5 else s.secondsLeft
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            // E-ink: a thin black track with a thick black arc, as there are no greys.
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(if (eink) 3.dp.toPx() else stroke))
            val done = 1f - shownLeft.toFloat() / s.phaseSeconds
            drawArc(ring, -90f, 360f * done, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shownLeft", fontSize = 64.sp, style = MaterialTheme.typography.displaySmall)
            Text("seconds", style = MaterialTheme.typography.bodyMedium, color = states.muted)
        }
    }
    // The live heart rate changes every second, so it is left off e-ink.
    if (!eink) Text(
        if (s.hr != null) "Heart rate ${s.hr}" else "",
        style = MaterialTheme.typography.bodyLarge,
        color = states.muted,
    )
    if (s.noContact) Warning("The strap isn't touching your skin. Wet the electrodes and tighten it.")
    else if (s.signalLost) Warning("Signal lost. Keep the phone near you.")
    Secondary("Stop", onClose)
}

@Composable
private fun PhaseSteps(phase: Phase, includeStanding: Boolean) {
    val steps = if (includeStanding) listOf(Phase.SETTLE, Phase.LYING, Phase.STANDING) else listOf(Phase.SETTLE, Phase.LYING)
    val names = mapOf(Phase.SETTLE to "Settle", Phase.LYING to "Lying", Phase.STANDING to "Standing")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEach { p ->
            val current = p == phase
            val past = p.ordinal < phase.ordinal
            Text(
                (if (past) "✓ " else "") + names.getValue(p),
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) MaterialTheme.colorScheme.primary else LocalStateColors.current.muted,
                fontSize = if (current) 18.sp else 16.sp,
                // Bold and underlined, so the current step stands out on e-ink too.
                fontWeight = if (current) androidx.compose.ui.text.font.FontWeight.Bold else null,
                textDecoration = if (current) androidx.compose.ui.text.style.TextDecoration.Underline else null,
            )
        }
    }
}

@Composable private fun Title(t: String) =
    Text(t, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable private fun Body(t: String) =
    Text(t, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable private fun Value(label: String, value: String) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }

@Composable private fun Warning(t: String) {
    val s = LocalStateColors.current
    Panel(color = s.warnSoft) { Text(t, style = MaterialTheme.typography.bodyLarge, color = s.warn) }
}

@Composable private fun Secondary(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(if (LocalEink.current) 2.dp else 1.dp, MaterialTheme.colorScheme.outline)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
