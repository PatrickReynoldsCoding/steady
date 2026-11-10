package com.patrickreynolds.steady.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings, as a snapshot. Times are minutes after midnight. */
data class Settings(
    val sensorAddress: String? = null,
    val sensorName: String? = null,
    val demoSensor: Boolean = false,
    val includeStanding: Boolean = true,
    val eveningReminder: Boolean = true,
    val eveningMinutes: Int = 20 * 60,
    val morningReminder: Boolean = false,
    val morningMinutes: Int = 8 * 60 + 30,
    val stepsAsked: Boolean = false,
)

class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings
    val current get() = _settings.value

    private fun load() = Settings(
        sensorAddress = sp.getString("sensorAddress", null),
        sensorName = sp.getString("sensorName", null),
        demoSensor = sp.getBoolean("demoSensor", false),
        includeStanding = sp.getBoolean("includeStanding", true),
        eveningReminder = sp.getBoolean("eveningReminder", true),
        eveningMinutes = sp.getInt("eveningMinutes", 20 * 60),
        morningReminder = sp.getBoolean("morningReminder", false),
        morningMinutes = sp.getInt("morningMinutes", 8 * 60 + 30),
        stepsAsked = sp.getBoolean("stepsAsked", false),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(current)
        sp.edit()
            .putString("sensorAddress", s.sensorAddress)
            .putString("sensorName", s.sensorName)
            .putBoolean("demoSensor", s.demoSensor)
            .putBoolean("includeStanding", s.includeStanding)
            .putBoolean("eveningReminder", s.eveningReminder)
            .putInt("eveningMinutes", s.eveningMinutes)
            .putBoolean("morningReminder", s.morningReminder)
            .putInt("morningMinutes", s.morningMinutes)
            .putBoolean("stepsAsked", s.stepsAsked)
            .apply()
        _settings.value = s
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context.applicationContext).also { instance = it } }
    }
}
