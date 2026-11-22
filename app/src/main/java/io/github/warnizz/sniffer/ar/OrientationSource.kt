package io.github.warnizz.sniffer.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Device→world rotation matrices from the rotation-vector sensor, lightly low-pass filtered. */
class OrientationSource(private val context: Context) {
    fun rotation(): Flow<FloatArray> = callbackFlow {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) { awaitClose { }; return@callbackFlow }
        val raw = FloatArray(9)
        var smoothed: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                val s = smoothed ?: raw.copyOf().also { smoothed = it }
                for (i in 0 until 9) s[i] += ALPHA * (raw[i] - s[i])
                trySend(s.copyOf())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }

    private companion object { const val ALPHA = 0.15f }
}
