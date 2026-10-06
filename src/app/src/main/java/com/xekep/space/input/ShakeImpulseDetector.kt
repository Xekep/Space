package com.xekep.space.input

import com.xekep.space.sim.Vec2
import kotlin.math.sqrt

/** A deliberate jolt, not tilt or ordinary handling. Inputs are gravity-free m/s². */
class ShakeImpulseDetector {
    private var started = -1L
    private var lastImpulse = -1L
    fun reset() { started = -1L; lastImpulse = -1L }

    fun sample(x: Double, y: Double, z: Double, nanos: Long, rotation: Int = 0): Vec2? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || nanos < 0L) return null
        if (started < 0L) started = nanos
        if (nanos - started < 250_000_000L || (lastImpulse >= 0L && nanos - lastImpulse < 650_000_000L)) return null
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < 12.0) return null
        var direction = when (rotation) {
            1 -> Vec2(y, x)
            2 -> Vec2(-x, y)
            3 -> Vec2(-y, -x)
            else -> Vec2(x, -y)
        }
        // A forward/backward shake still gives a visible kick in the two-dimensional world.
        if (direction.magnitude() < magnitude * 0.25) direction = Vec2(z * 0.6, -z * 0.8)
        lastImpulse = nanos
        return direction.normalized() * (20.0 + (magnitude - 12.0) * 4.0).coerceAtMost(80.0)
    }
}
