package com.xekep.space.ui.space

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.spaceGestures(game: SpaceGameState, hasSession: Boolean): Modifier = pointerInput(game, game.menuOpen, hasSession, game.sandboxOverlayOpen) {
    if (game.menuOpen || !hasSession || game.sandboxOverlayOpen) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        game.touchPreview = game.previewAt(down.position, down.position, startedAt)
        var cameraGesture = false
        try {
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    // Once a second finger joins, this entire gesture belongs to the camera,
                    // including the final single-finger release. Never launch a stray body.
                    game.touchPreview = null
                    cameraGesture = true
                    if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                        game.transformCamera(event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom())
                    }
                } else if (!cameraGesture) {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        game.touchPreview = game.previewAt(down.position, change.position, startedAt)
                        if (!change.pressed && !change.isConsumed) {
                            game.touchPreview?.let {
                                val hold = (SystemClock.elapsedRealtimeNanos() - it.startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0
                                game.finishGesture(it, hold)
                            }
                        }
                    }
                }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } finally {
            game.touchPreview = null
        }
    }
}
