package com.xekep.space.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import com.xekep.space.sim.Vec2
import kotlin.math.exp

class SpaceShake(context: Context, private val onImpulse: (Vec2) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available: Boolean get() = sensor != null
    private val detector = ShakeImpulseDetector()
    private val gravity = DoubleArray(3)
    private var previous = 0L
    private var enabled = false
    private var foreground = false
    private var registered = false
    fun setEnabled(value: Boolean) { enabled = value; synchronize() }
    fun setForeground(value: Boolean) { foreground = value; synchronize() }
    fun close() { enabled = false; foreground = false; synchronize() }

    private fun synchronize() {
        val active = enabled && foreground && available
        if (active == registered) return
        detector.reset(); previous = 0L
        registered = if (active) manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
            else { manager?.unregisterListener(this); false }
    }

    @Suppress("DEPRECATION")
    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.values.size < 3) return
        val values = DoubleArray(3) { event.values[it].toDouble() }
        if (values.any { !it.isFinite() }) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (previous == 0L) values.copyInto(gravity)
            val dt = if (previous == 0L) 0.0 else (event.timestamp - previous) / 1e9
            val alpha = exp(-dt.coerceIn(0.0, 0.2) / 0.35)
            for (i in values.indices) {
                gravity[i] = alpha * gravity[i] + (1.0 - alpha) * values[i]
                values[i] -= gravity[i]
            }
        }
        previous = event.timestamp
        detector.sample(values[0], values[1], values[2], event.timestamp, window?.defaultDisplay?.rotation ?: 0)?.let(onImpulse)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
