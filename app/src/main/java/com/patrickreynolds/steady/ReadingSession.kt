package com.patrickreynolds.steady

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrickreynolds.steady.analysis.Beat
import com.patrickreynolds.steady.analysis.Hrv
import com.patrickreynolds.steady.analysis.Phase
import com.patrickreynolds.steady.analysis.ReadingMetrics
import com.patrickreynolds.steady.data.Prefs
import com.patrickreynolds.steady.data.Store
import com.patrickreynolds.steady.sensor.BleHeartSource
import com.patrickreynolds.steady.sensor.DemoHeartSource
import com.patrickreynolds.steady.sensor.HeartEvent
import com.patrickreynolds.steady.steps.PhoneSteps
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface ReadingUi {
    data object Idle : ReadingUi
    data object Connecting : ReadingUi
    data class Measuring(
        val phase: Phase,
        val secondsLeft: Int,
        val phaseSeconds: Int,
        val hr: Int?,
        val signalLost: Boolean,
        val noContact: Boolean,
        val includeStanding: Boolean,
    ) : ReadingUi
    data class Done(val metrics: ReadingMetrics) : ReadingUi
    data class Failed(val message: String) : ReadingUi
}

/**
 * Runs one morning reading: wait for the strap, 20 s to settle, 60 s lying still, then (if turned
 * on) 60 s standing. Beeps and buzzes at each change so the screen never needs reading.
 */
class ReadingSession(app: Application) : AndroidViewModel(app) {
    companion object {
        const val SETTLE_MS = 20_000L
        const val LYING_MS = 60_000L
        const val STANDING_MS = 60_000L
        const val FIND_TIMEOUT_MS = 45_000L
        const val SIGNAL_LOST_MS = 4_000L
    }

    private val _state = MutableStateFlow<ReadingUi>(ReadingUi.Idle)
    val state: StateFlow<ReadingUi> = _state
    private var job: Job? = null
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()

    fun start() {
        job?.cancel()
        val settings = Prefs.get(getApplication()).current
        val includeStanding = settings.includeStanding
        val source = when {
            settings.demoSensor -> DemoHeartSource(SETTLE_MS + LYING_MS)
            settings.sensorAddress != null -> BleHeartSource(getApplication(), settings.sensorAddress)
            else -> { _state.value = ReadingUi.Failed("Choose your strap in Settings first."); return }
        }
        _state.value = ReadingUi.Connecting
        val beats = mutableListOf<Beat>()
        val startedAtWall = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()

        job = viewModelScope.launch {
            var phase: Phase? = null
            var phaseStart = 0L
            var lastPacket = 0L
            var hr: Int? = null
            var noContact = false

            // Everything runs on the main thread, so the collector and the clock below never race.
            val collector = launch {
                source.events().collect { e ->
                    when (e) {
                        is HeartEvent.Packet -> {
                            val now = SystemClock.elapsedRealtime()
                            lastPacket = now
                            hr = e.packet.hr
                            noContact = e.packet.contact == false
                            if (phase == null && e.packet.rrMs.isNotEmpty()) {
                                phase = Phase.SETTLE; phaseStart = now; cue(Cue.START)
                            }
                            phase?.let { p -> e.packet.rrMs.forEach { beats += Beat(p, now - phaseStart, it) } }
                        }
                        is HeartEvent.Failed -> _state.value = ReadingUi.Failed(e.message)
                        HeartEvent.Connected, HeartEvent.Disconnected -> Unit
                    }
                }
            }

            while (true) {
                delay(250)
                if (_state.value is ReadingUi.Failed) { collector.cancel(); return@launch }
                val now = SystemClock.elapsedRealtime()
                val p = phase
                if (p == null) {
                    if (now - startedAt > FIND_TIMEOUT_MS) {
                        collector.cancel()
                        _state.value = ReadingUi.Failed(
                            "Couldn't find your strap. Wet the electrodes, put it on, and try again."
                        )
                        return@launch
                    }
                    continue
                }
                val length = when (p) { Phase.SETTLE -> SETTLE_MS; Phase.LYING -> LYING_MS; Phase.STANDING -> STANDING_MS }
                if (now - phaseStart >= length) {
                    val next = when (p) {
                        Phase.SETTLE -> Phase.LYING
                        Phase.LYING -> if (includeStanding) Phase.STANDING else null
                        Phase.STANDING -> null
                    }
                    if (next == null) {
                        collector.cancel()
                        finish(beats, startedAtWall)
                        return@launch
                    }
                    phase = next; phaseStart = now
                    if (next == Phase.STANDING) cue(Cue.STAND)
                    continue
                }
                _state.value = ReadingUi.Measuring(
                    phase = p,
                    secondsLeft = ((length - (now - phaseStart) + 999) / 1000).toInt(),
                    phaseSeconds = (length / 1000).toInt(),
                    hr = hr,
                    signalLost = now - lastPacket > SIGNAL_LOST_MS,
                    noContact = noContact,
                    includeStanding = includeStanding,
                )
            }
        }
    }

    private fun finish(beats: List<Beat>, startedAtWall: Long) {
        val metrics = Hrv.analyse(beats)
        val app = getApplication<Application>()
        Store.get(app).saveReading(LocalDate.now(), startedAtWall, metrics, beats)
        cue(Cue.DONE)
        _state.value = ReadingUi.Done(metrics)
        viewModelScope.launch { PhoneSteps.sample(app) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ReadingUi.Idle
    }

    private enum class Cue { START, STAND, DONE }

    private fun cue(cue: Cue) {
        when (cue) {
            Cue.START -> tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Cue.STAND -> tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            Cue.DONE -> tones?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 600)
        }
        val pattern = when (cue) {
            Cue.START -> longArrayOf(0, 150)
            Cue.STAND -> longArrayOf(0, 300, 150, 300)
            Cue.DONE -> longArrayOf(0, 600)
        }
        runCatching {
            val app = getApplication<Application>()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(android.os.Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    override fun onCleared() {
        job?.cancel()
        tones?.release()
    }
}
