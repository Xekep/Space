package com.xekep.space.input

import com.xekep.space.sim.Vec2
import com.xekep.space.sim.ShakeMode
import kotlin.math.sqrt

/** A deliberate jolt, not tilt or ordinary handling. Inputs are gravity-free m/s². */
class ShakeImpulseDetector {
    companion object { const val MAX_IMPULSE = 320.0 }
    private var started = -1L
    private var lastImpulse = -1L
    private var armed = true
    private var previousDirection = Vec2.Zero
    fun reset() { started = -1L; lastImpulse = -1L; armed = true; previousDirection = Vec2.Zero }

    fun sample(x: Double, y: Double, z: Double, nanos: Long, rotation: Int = 0, mode: ShakeMode = ShakeMode.Inertial): Vec2? {
        if (mode == ShakeMode.Off) return null
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || nanos < 0L) return null
        if (started < 0L) started = nanos
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite()) return null
        if (magnitude < 4.0) armed = true
        val classic=mode == ShakeMode.Classic
        if (nanos - started < 250_000_000L || (lastImpulse >= 0L && nanos - lastImpulse < if (classic) 650_000_000L else 200_000_000L) || magnitude < if (classic) 12.0 else 8.0) return null
        var direction = when (rotation) {
            1 -> Vec2(y, x)
            2 -> Vec2(-x, y)
            3 -> Vec2(-y, -x)
            else -> Vec2(x, -y)
        }
        // A forward/backward shake still gives a visible kick in the two-dimensional world.
        if (direction.magnitude() < magnitude * 0.25) direction = Vec2(z * 0.6, -z * 0.8)
        direction = direction.normalized()
        if (classic) {
            lastImpulse=nanos
            return direction*(31.25+(magnitude-12)*6.25).coerceAtMost(125.0)
        }
        val reversed = direction.x * previousDirection.x + direction.y * previousDirection.y < -0.35
        if (!armed && !reversed) return null
        armed = false
        previousDirection = direction
        lastImpulse = nanos
        // Contents lag behind the phone, like loose balls in a shaken container.
        return direction * -(80.0 + (magnitude - 8.0) * 14.0).coerceAtMost(MAX_IMPULSE)
    }
}
