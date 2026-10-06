package com.xekep.space.sim

import kotlin.math.cos
import kotlin.math.sin

enum class ShakeMode { Off, Classic, Inertial }

/** Small per-body deflections make a jolt stir the scene instead of translating it rigidly. */
fun shakeVelocityDelta(bodyId: Long, impulse: Vec2, zoom: Float, density: Float): Vec2 {
    val angle = (Math.floorMod(bodyId, 11L) / 10.0 - .5) * .8
    val strength = .8 + Math.floorMod(bodyId, 7L) / 6.0 * .4
    val viewScale = (density.toDouble() / zoom.coerceAtLeast(.005f)).coerceIn(.5, 8.0)
    return Vec2(impulse.x * cos(angle) - impulse.y * sin(angle),
        impulse.x * sin(angle) + impulse.y * cos(angle)) * (strength * viewScale)
}
