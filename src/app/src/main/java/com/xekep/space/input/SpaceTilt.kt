package com.xekep.space.input

import android.content.Context
import android.hardware.*
import android.view.WindowManager
import com.xekep.space.sim.Vec2
import kotlin.math.exp

class SpaceTilt(context: Context, private val onDirection: (Vec2) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available: Boolean get() = sensor != null
    private val detector = TiltSteeringDetector()
    private val quaternion = FloatArray(4)
    private val gravity = DoubleArray(3)
    private var previous = 0L
    private var enabled = false
    private var foreground = false
    private var registered = false
    fun recalibrate() { detector.reset(); previous = 0L; onDirection(Vec2.Zero) }
    fun setEnabled(value: Boolean) { enabled = value; synchronize() }
    fun setForeground(value: Boolean) { foreground = value; synchronize() }
    fun close() { enabled = false; foreground = false; synchronize() }
    private fun synchronize() {
        val active = enabled && foreground && available
        if (active == registered) return
        recalibrate()
        registered = if (active) manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
            else { manager?.unregisterListener(this); false }
    }
    @Suppress("DEPRECATION")
    override fun onSensorChanged(event: SensorEvent) {
        if (!registered || event.values.size < 3 || event.values.any { !it.isFinite() }) return
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getQuaternionFromVector(quaternion,event.values)
            onDirection(detector.sampleQuaternion(quaternion[1].toDouble(),quaternion[2].toDouble(),quaternion[3].toDouble(),quaternion[0].toDouble(),event.timestamp,window?.defaultDisplay?.rotation ?: 0))
            return
        }
        val values = DoubleArray(3) { event.values[it].toDouble() }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (previous == 0L) values.copyInto(gravity)
            val alpha = exp(-((event.timestamp - previous) / 1e9).coerceIn(0.0, .2) / .18)
            for (i in values.indices) { gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]; values[i] = gravity[i] }
        }
        previous = event.timestamp
        onDirection(detector.sample(values[0], values[1], values[2], event.timestamp, window?.defaultDisplay?.rotation ?: 0))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
