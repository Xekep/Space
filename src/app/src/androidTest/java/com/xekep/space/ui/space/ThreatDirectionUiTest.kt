package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThreatDirectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Suppress("UNCHECKED_CAST")
    private fun replace(game: SpaceGameState, session: ArcadeSession) {
        val field = SpaceGameState::class.java.getDeclaredField("arcade\$delegate")
        field.isAccessible = true
        (field.get(game) as MutableState<ArcadeSession?>).value = session
    }

    private fun rotateCamera(game: SpaceGameState, rotation: Double) {
        val field = SpaceGameState::class.java.getDeclaredField("cameraRotation\$delegate")
        field.isAccessible = true
        (field.get(game) as MutableDoubleState).doubleValue = rotation
    }

    @Test fun incomingMissedAndPendingThreatsPointOutwardAtEveryScreenEdge() {
        val game = SpaceGameState().apply { resize(IntSize(825, 1100)); startArcade() }
        compose.setContent {
            Canvas(Modifier.size(300.dp, 400.dp).onSizeChanged(game::resize).testTag("indicators")) {
                drawRect(Color.Black)
                drawSpaceIndicators(game)
            }
        }
        val w = game.viewport.width.toFloat(); val h = game.viewport.height.toFloat()
        val positions = listOf(Offset(-100f, h / 2), Offset(w + 100f, h / 2), Offset(w / 2, -100f), Offset(w / 2, h + 100f))
        val inward = listOf(Vec2(100.0, 0.0), Vec2(-100.0, 0.0), Vec2(0.0, 100.0), Vec2(0.0, -100.0))
        for (rotation in listOf(0.0, Math.PI / 2, Math.PI, -.7))
            for ((side, point) in positions.withIndex()) for (pending in listOf(false, true)) for (incoming in listOf(true, false)) {
            compose.runOnIdle {
                rotateCamera(game, rotation)
                val velocity = rotateVector(inward[side] * if (incoming) 1.0 else -1.0, -rotation)
                val body = CelestialBody(999, screenToWorld(point, game.viewport, game.camera.center, game.camera.zoom, rotation),
                    velocity, 90.0, 10f, Color.Red, BodyKind.Meteor)
                val core = game.arcade!!.bodies.first { it.kind == BodyKind.Core }
                replace(game, game.arcade!!.copy(bodies = if (pending) listOf(core) else listOf(core, body),
                    pending = if (pending) listOf(PendingThreat(body, 1.2)) else emptyList()))
            }
            val image = compose.onNodeWithTag("indicators").captureToImage().asAndroidBitmap()
            var count = 0
            var minX = image.width; var maxX = 0; var minY = image.height; var maxY = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = image.getPixel(x, y)
                if (android.graphics.Color.red(color) > 250 && android.graphics.Color.green(color) in 134..142 &&
                    android.graphics.Color.blue(color) in 87..95) {
                    count++; minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                }
            }
            assertTrue("Missing threat indicator", count > 20)
            val mx = 24 * compose.density.density
            val my = minOf(160 * compose.density.density, h * .28f)
            val label = "rotation=$rotation, side=$side, pending=$pending, incoming=$incoming"
            // The arrow tip is on the safe edge, its shaft extends back into the screen.
            when (side) {
                0 -> assertTrue(label, minX >= mx - 4)
                1 -> assertTrue(label, maxX <= w - mx + 4)
                2 -> assertTrue(label, minY >= my - 4)
                3 -> assertTrue(label, maxY <= h - my + 4)
            }
        }
    }
}
