package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.BodyKind

internal data class CoreDirectionMarker(val position: Offset, val direction: Offset)

/** Hide while any part of the core's disk is visible; project its direction onto a safe edge. */
internal fun coreDirectionMarker(point: Offset, radius: Float, viewport: IntSize, bounds: Rect): CoreDirectionMarker? {
    if (viewport.width <= 0 || viewport.height <= 0 || !point.x.isFinite() || !point.y.isFinite() ||
        !radius.isFinite() || radius < 0f || bounds.width <= 0f || bounds.height <= 0f) return null
    val nearest = Offset(point.x.coerceIn(0f, viewport.width.toFloat()), point.y.coerceIn(0f, viewport.height.toFloat()))
    if ((point - nearest).getDistance() <= radius) return null
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    if (!bounds.contains(center)) return null
    val delta = point - center
    val x = if (delta.x == 0f) Float.POSITIVE_INFINITY else
        ((if (delta.x > 0f) bounds.right else bounds.left) - center.x) / delta.x
    val y = if (delta.y == 0f) Float.POSITIVE_INFINITY else
        ((if (delta.y > 0f) bounds.bottom else bounds.top) - center.y) / delta.y
    return CoreDirectionMarker(center + delta * minOf(x, y), delta / delta.getDistance())
}

@Composable
fun CoreDirectionIndicator(game: SpaceGameState) {
    if (game.mode != AppMode.Arcade || game.menuOpen || (game.arcade?.lives ?: 0) <= 0) return
    val core = game.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val padding = with(density) { 18.dp.toPx() }
    val viewport = game.viewport
    val bounds = Rect(insets.getLeft(density, direction) + padding, insets.getTop(density) + padding,
        viewport.width - insets.getRight(density, direction) - padding, viewport.height - insets.getBottom(density) - padding)
    val marker = coreDirectionMarker(worldToScreen(core.position, viewport, game.camera.center, game.camera.zoom, game.cameraRotation),
        bodyScreenRadius(core, game.camera.zoom, density.density), viewport, bounds) ?: return
    val label = stringResource(R.string.direction_to_core)
    Canvas(Modifier.fillMaxSize().testTag("core-direction").semantics { contentDescription = label }) {
        val color = Color(0xFFFFD166)
        drawCircle(color.copy(alpha = .2f), 12.dp.toPx(), marker.position)
        drawArrow(marker.position - marker.direction * 16.dp.toPx(), marker.position, color)
    }
}
