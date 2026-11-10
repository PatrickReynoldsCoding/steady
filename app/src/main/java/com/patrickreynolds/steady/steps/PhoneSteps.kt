package com.patrickreynolds.steady.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.patrickreynolds.steady.analysis.StepSample
import com.patrickreynolds.steady.analysis.Steps
import com.patrickreynolds.steady.data.Store
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Daily steps from the phone's own step counter, so no other app or wearable is needed. The
 * counter is read every 15 minutes in the background (when Android allows it), when the app
 * opens, and after each reading. The difference since the last read is added to that day.
 */
object PhoneSteps {
    fun available(context: Context) =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun permitted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Reads the counter once and stores the steps since the last read. */
    suspend fun sample(context: Context) {
        if (!permitted(context)) return
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val count = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Long> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        } ?: return
        val store = Store.get(context)
        val next = StepSample(System.currentTimeMillis(), count)
        store.addStepSample(next, Steps.delta(store.lastStepSample(), next, ZoneId.systemDefault()))
    }

    fun schedule(context: Context) {
        if (!permitted(context)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "phone-steps",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

class StepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PhoneSteps.sample(applicationContext)
        return Result.success()
    }
}
