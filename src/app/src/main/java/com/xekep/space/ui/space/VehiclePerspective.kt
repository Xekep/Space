package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Matrix
import kotlin.math.cos
import kotlin.math.sin

/** Local homography: the nearer nose/tail grows, the farther end shrinks. */
internal fun vehiclePitchMatrix(pitch: Double,radius: Float): Matrix = Matrix().apply {
    values[5]=cos(pitch).toFloat()
    values[7]=if (radius > 0f) (sin(pitch)/(4*radius)).toFloat() else 0f
}
