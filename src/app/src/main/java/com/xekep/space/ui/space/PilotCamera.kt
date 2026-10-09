package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import kotlin.math.exp
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import com.xekep.space.sim.CelestialBody

private const val PILOT_CAMERA_LAG_SECONDS = .30

internal fun pilotAnchorOffset(viewport: IntSize): Vec2 = Vec2(0.0,minOf(viewport.width,viewport.height).coerceAtLeast(1)*.10)

internal fun pilotCameraTarget(body: CelestialBody): Double =
    -PI/2-atan2(body.heading.y,body.heading.x)-(body.roll*.16).coerceIn(-PI/22.5,PI/22.5)

internal fun pilotCameraRotation(current: Double, target: Double, seconds: Double, piloting: Boolean): Double {
    if (!seconds.isFinite() || seconds <= 0) return current
    val difference=atan2(sin(target-current),cos(target-current))
    val next=current+difference*(1-exp(-seconds.coerceAtMost(.1)/(if (piloting) PILOT_CAMERA_LAG_SECONDS else .18)))
    return atan2(sin(next),cos(next))
}

/** Soft pursuit with a screen-space leash: zoom never ejects the pilot from the frame. */
internal fun pilotCameraCenter(current: Vec2, position: Vec2, zoom: Float, viewport: IntSize, visualOffset: Vec2 = Vec2.Zero, seconds: Double = 1.0/60): Vec2 {
    val desired=position-visualOffset
    val distance=(desired-current).magnitude()
    val deadZone=minOf(viewport.width,viewport.height).coerceAtLeast(1)*.02/zoom.coerceAtLeast(.000001f)
    val remainder=if (distance > deadZone) deadZone+(distance-deadZone)*exp(-seconds.coerceIn(0.0,.1)/PILOT_CAMERA_LAG_SECONDS) else distance
    val lag=if (distance > 0.0) (desired-current)*(remainder/distance) else Vec2.Zero
    val maximum=minOf(viewport.width,viewport.height).coerceAtLeast(1)*.18/zoom.coerceAtLeast(.000001f)
    val behind=position-(desired-lag)
    val length=behind.magnitude()
    return position-if (length > maximum) behind*(maximum/length) else behind
}
