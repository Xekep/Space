package com.xekep.space.ui.space

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** The same immediate, captured drag for both speed scales, including a paused sandbox. */
internal fun Modifier.pilotSpeedInput(game: SpaceGameState,enabled: Boolean,limit: Double,vertical: Boolean): Modifier =
    pointerInput(game,enabled,game.controlledVehicleId,limit,vertical) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false); down.consume()
            fun move(point: Offset) {
                val fraction=if (vertical) 1-point.y/size.height.coerceAtLeast(1) else point.x/size.width.coerceAtLeast(1)
                if (fraction.isFinite()) game.setPilotTargetSpeed(fraction.coerceIn(0f,1f)*limit)
            }
            move(down.position)
            do {
                val change=awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                change.consume(); move(change.position)
                if (!change.pressed) break
            } while (true)
        }
    }
