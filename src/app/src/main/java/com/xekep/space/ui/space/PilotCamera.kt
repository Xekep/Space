package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import kotlin.math.exp

/** Soft pursuit with a screen-space leash: zoom never ejects the pilot from the frame. */
internal fun pilotCameraCenter(current: Vec2, position: Vec2, zoom: Float, viewport: IntSize, visualOffset: Vec2 = Vec2.Zero): Vec2 {
    val desired=position-visualOffset
    val lag=(desired-current)*exp(-(1.0/60)/.18)
    val maximum=minOf(viewport.width,viewport.height).coerceAtLeast(1)*.18/zoom.coerceAtLeast(.000001f)
    val behind=position-(desired-lag)
    val length=behind.magnitude()
    return position-if (length > maximum) behind*(maximum/length) else behind
}
