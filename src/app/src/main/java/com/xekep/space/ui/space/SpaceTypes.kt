package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import kotlin.math.hypot

const val MaxEnergy = 120.0
const val EnergyRegenPerSecond = 24.0
const val ScorePerSecond = 8.0
const val StartingCoreLives = 4
const val CullMargin = 260.0
const val SandboxTimeScale = 6.0
const val ArcadeMinZoom = 0.55f
const val ArcadeMaxZoom = 3.0f
const val SandboxMinZoom = 0.03f
const val SandboxMaxZoom = 12.0f

enum class AppMode {
    Arcade,
    Sandbox,
}

data class TouchPreview(
    val startWorld: Vec2,
    val currentWorld: Vec2,
    val startedAtNanos: Long,
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
): Offset {
    return Offset(
        x = (((world.x - cameraCenter.x) * zoom) + (viewport.width / 2f)).toFloat(),
        y = (((world.y - cameraCenter.y) * zoom) + (viewport.height / 2f)).toFloat(),
    )
}

fun screenToWorld(
    screen: Offset,
    viewport: IntSize,
    cameraCenter: Vec2,
    zoom: Float,
): Vec2 {
    return Vec2(
        x = ((screen.x - (viewport.width / 2f)) / zoom) + cameraCenter.x,
        y = ((screen.y - (viewport.height / 2f)) / zoom) + cameraCenter.y,
    )
}

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
