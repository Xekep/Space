package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.isVehicle

/** Small oblique-view parallax reveals altitude immediately without moving the world plane. */
internal fun flightRenderPosition(body: CelestialBody,position: Vec2,viewport: IntSize,zoom: Float,rotation: Double): Vec2 {
    if (!body.isVehicle || body.flightHeight == 0.0) return position
    val pixels=-minOf(viewport.width,viewport.height)*.055*tanh(body.flightHeight/maxOf(12.0,body.radius*1.5))
    return position+rotateVector(Vec2(0.0,pixels/zoom.coerceAtLeast(.000001f)),-rotation)
}

/** Perspective projection of a rotated local hull plane. Nose is -Y, camera is above +Z.
 * Roll rotates about the longitudinal axis, pitch about the lateral axis. */
internal fun vehiclePitchMatrix(pitch: Double,radius: Float,roll: Double = 0.0): Matrix = Matrix().apply {
    values[0]=cos(roll).toFloat()
    values[1]=(sin(roll)*sin(pitch)).toFloat()
    values[3]=if (radius > 0f) (-sin(roll)*cos(pitch)/(4*radius)).toFloat() else 0f
    values[5]=cos(pitch).toFloat()
    values[7]=if (radius > 0f) (sin(pitch)/(4*radius)).toFloat() else 0f
}

/** The same projection at any local hull depth, used by the extruded side faces. */
internal fun vehicleHullPoint(point: Offset, depth: Float, pitch: Double, roll: Double, radius: Float): Offset {
    val cr=cos(roll); val sr=sin(roll); val cp=cos(pitch); val sp=sin(pitch)
    val x=point.x*cr-depth*sr
    val y=point.y*cp+(point.x*sr+depth*cr)*sp
    val z=-point.y*sp+(point.x*sr+depth*cr)*cp
    val w=if (radius > 0f) (1-z/(4*radius)).coerceAtLeast(.15) else 1.0
    return Offset((x/w).toFloat(),(y/w).toFloat())
}
