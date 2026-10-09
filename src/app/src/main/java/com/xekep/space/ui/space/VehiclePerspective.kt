package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Matrix
import kotlin.math.cos
import kotlin.math.sin

/** Local homography: the nearer nose/tail grows, the farther end shrinks. */
internal fun vehiclePitchMatrix(pitch: Double,radius: Float,roll: Double = 0.0): Matrix = Matrix().apply {
    values[0]=cos(roll).toFloat()
    values[4]=(sin(roll)*.22).toFloat()
    values[3]=if (radius > 0f) (sin(roll)/(5*radius)).toFloat() else 0f
    values[5]=cos(pitch).toFloat()
    values[7]=if (radius > 0f) (sin(pitch)/(4*radius)).toFloat() else 0f
}
