package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

const val MaxEnergy = 120.0
const val EnergyRegenPerSecond = 12.0
const val StartingCoreLives = 4
const val CullMargin = 260.0
const val ArcadeMinZoom = 0.15f
const val ArcadeMaxZoom = 6.0f
const val SandboxMinZoom = 0.005f
const val SandboxMaxZoom = 250.0f

enum class AppMode {
    Arcade,
    Sandbox,
}

data class TouchPreview(
    val startWorld: Vec2,
    val currentWorld: Vec2,
    val startedAtNanos: Long,
    val dragDp: Offset? = null,
    val waypoints: List<Vec2> = emptyList(),
)

data class BackgroundStar(
    val position: Offset,
    val radius: Float,
    val alpha: Float,
)

fun worldToScreen(
    world: Vec2,
    viewport: IntSize,
    cameraCenter: Vec2,
    zoom: Float,
    rotation: Double = 0.0,
): Offset {
    val x=(world.x-cameraCenter.x)*zoom
    val y=(world.y-cameraCenter.y)*zoom
    val c=if (rotation == 0.0) 1.0 else cos(rotation)
    val s=if (rotation == 0.0) 0.0 else sin(rotation)
    return Offset(
        x = (x*c-y*s + viewport.width / 2f).toFloat(),
        y = (x*s+y*c + viewport.height / 2f).toFloat(),
    )
}

fun screenToWorld(
    screen: Offset,
    viewport: IntSize,
    cameraCenter: Vec2,
    zoom: Float,
    rotation: Double = 0.0,
): Vec2 {
    val x=((screen.x-viewport.width/2f)/zoom).toDouble()
    val y=((screen.y-viewport.height/2f)/zoom).toDouble()
    val c=if (rotation == 0.0) 1.0 else cos(rotation)
    val s=if (rotation == 0.0) 0.0 else sin(rotation)
    return Vec2(cameraCenter.x+x*c+y*s,cameraCenter.y-x*s+y*c)
}

fun rotateVector(vector: Vec2, radians: Double): Vec2 = if (radians == 0.0) vector else
    Vec2(vector.x*cos(radians)-vector.y*sin(radians),vector.x*sin(radians)+vector.y*cos(radians))

fun distance(first: Offset, second: Offset): Float {
    return hypot((second.x - first.x).toDouble(), (second.y - first.y).toDouble()).toFloat()
}

fun minimumZoom(mode: AppMode): Float {
    return when (mode) {
        AppMode.Arcade -> ArcadeMinZoom
        AppMode.Sandbox -> SandboxMinZoom
    }
}

fun maximumZoom(mode: AppMode): Float {
    return when (mode) {
        AppMode.Arcade -> ArcadeMaxZoom
        AppMode.Sandbox -> SandboxMaxZoom
    }
}
