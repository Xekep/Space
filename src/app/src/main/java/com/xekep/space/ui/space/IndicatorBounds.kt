package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/** Layout rectangles are measured in root pixels; drawing happens in scene-local pixels. */
internal fun indicatorBounds(fallback: Rect, origin: Offset, top: Rect?, bottom: Rect?, padding: Float): Rect {
    val first=top?.let { it.bottom-origin.y } ?: fallback.top
    val last=bottom?.let { it.top-origin.y } ?: fallback.bottom
    if (!first.isFinite() || !last.isFinite() || last <= first || fallback.width <= 0f) return Rect.Zero
    val gap=last-first
    val inset=minOf(padding.coerceAtLeast(0f),gap/4)
    return Rect(fallback.left,first+inset,fallback.right,last-inset)
}

internal fun SpaceGameState.indicatorBounds(fallback: Rect,padding: Float): Rect =
    indicatorBounds(fallback,sceneOrigin,arcadeHudTop,arcadeHudBottom,padding)

internal fun indicatorAnchor(viewport: IntSize,bounds: Rect): Offset {
    val center=Offset(viewport.width/2f,viewport.height/2f)
    return if (bounds.contains(center)) center else bounds.center
}
