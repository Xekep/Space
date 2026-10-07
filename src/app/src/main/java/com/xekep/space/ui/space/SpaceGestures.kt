package com.xekep.space.ui.space

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.xekep.space.sim.*

private data class WaypointTap(val id: PointerId, val start: Offset, val primary: Offset)

fun Modifier.spaceGestures(game: SpaceGameState, hasSession: Boolean): Modifier = pointerInput(game, game.menuOpen, hasSession, game.sandboxOverlayOpen) {
    if (game.menuOpen || !hasSession || game.sandboxOverlayOpen) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val initial=game.previewAt(down.position,down.position,startedAt)
        val tapped=if (game.orbitSourceId == null) game.bodyAt(initial.startWorld)?.id else null
        game.touchPreview = initial.copy(tapBodyId=tapped)
        var cameraGesture = false
        var launched = false
        val vehicle = game.spawnKind == BodyKind.Ship || game.spawnKind == BodyKind.Rocket
        val points = mutableListOf<Vec2>()
        var waypointTap: WaypointTap? = null
        fun addPoint(position: Offset) {
            val point=game.worldAt(position)
            val previous=points.lastOrNull() ?: game.worldAt(down.position)
            if (points.size < MAX_WAYPOINTS && (point-previous).magnitude()*game.camera.zoom > 4*game.density) points += point
        }
        try {
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                val primary=event.changes.firstOrNull { it.id == down.id }
                if (!cameraGesture && !launched && pressed >= 2 && waypointTap == null) {
                    val secondary=event.changes.firstOrNull { it.id != down.id && it.pressed && !it.previousPressed && !it.isConsumed }
                    if (vehicle && game.controlledVehicleId == null && primary?.pressed == true && secondary != null && pressed == 2 &&
                        secondary.uptimeMillis-down.uptimeMillis >= 250) {
                        waypointTap=WaypointTap(secondary.id,secondary.position,primary.position)
                    } else { cameraGesture=true; game.touchPreview=null }
                }
                waypointTap?.let { tap ->
                    val secondary=event.changes.firstOrNull { it.id == tap.id }
                    if (pressed > 2 || primary == null || secondary == null ||
                        (primary.position-tap.primary).getDistance() > 12*game.density ||
                        (secondary.position-tap.start).getDistance() > 12*game.density) {
                        cameraGesture=true; waypointTap=null; game.touchPreview=null
                    } else if (!secondary.pressed || !primary.pressed) {
                        addPoint(secondary.position); waypointTap=null
                    }
                }
                if (cameraGesture) {
                    if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                        game.transformCamera(event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom())
                    }
                } else if (!launched) {
                    val change = primary
                    if (change != null) {
                        val pending=waypointTap?.let { tap -> event.changes.firstOrNull { it.id == tap.id }?.position }?.let(game::worldAt)
                        val route=points.toList()+listOfNotNull(pending).take(if (points.size < MAX_WAYPOINTS) 1 else 0)
                        game.touchPreview = game.previewAt(down.position, change.position, startedAt).copy(startWorld=initial.startWorld,waypoints=route,tapBodyId=tapped)
                        if (!change.pressed && !change.isConsumed) {
                            game.touchPreview?.let {
                                val hold = (change.uptimeMillis-down.uptimeMillis).coerceAtLeast(0L)/1000.0
                                game.finishGesture(it, hold)
                            }
                            launched=true; game.touchPreview=null
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
