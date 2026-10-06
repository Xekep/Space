package com.xekep.space.ui.space

import com.xekep.space.R
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.ShakeMode
import com.xekep.space.storage.GameOptions
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SandboxToolsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectingEditingDeletingAndUndoingWorkThroughTheUi() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
        compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
        compose.onNodeWithTag("edit-body").performClick()
        compose.onNodeWithTag("body-mass").performTextReplacement("500")
        compose.onNodeWithTag("apply-body-edit").performClick()
        compose.runOnIdle { assertEquals(500.0, game.bodies.single().mass, 0.0) }
        compose.onNodeWithTag("delete-body").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        compose.runOnIdle { assertTrue(game.bodies.isEmpty()) }
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.onNodeWithTag("sandbox-undo").performClick()
        compose.runOnIdle { assertEquals(500.0, game.bodies.single().mass, 0.0) }
    }
    @Test fun orbitHelperCreatesASatelliteAroundTheSelectedParent() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.waitForIdle()
        val parent = game.bodies.first()
        val position = worldToScreen(parent.position, game.viewport, game.camera.center, game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(position) }
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.onNodeWithTag("space-scene").performTouchInput { click(position + Offset(0f, 230f)) }
        compose.runOnIdle {
            assertEquals(3, game.bodies.size)
            val satellite = game.bodies.last()
            assertEquals(SimulationEngine.orbitVelocity(parent, satellite.position), satellite.velocity)
        }
    }
    @Test fun sandboxPracticeAdvancesWithActionsAndCanBeSkipped() {
        val game = SpaceGameState()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("practice-controls").performClick()
        compose.runOnIdle { assertEquals(0, game.tutorialStep); assertTrue(game.sandbox!!.paused) }
        compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, game.tutorialStep) }
        compose.onNodeWithTag("sandbox-pause").performClick()
        compose.runOnIdle { assertEquals(2, game.tutorialStep) }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.skip)).performClick()
        compose.runOnIdle { assertEquals(-1, game.tutorialStep) }
    }

    @Test fun normalHudHidesAdvancedActionsAndPanelsSuspendTimeWithoutChangingPause() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("sandbox-undo").assertDoesNotExist()
        compose.onNodeWithTag("speed-6.0").assertDoesNotExist()
        compose.onNodeWithTag("edit-body").assertDoesNotExist()
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.runOnIdle {
            val bodies = game.bodies
            game.update(0.2)
            assertEquals(bodies, game.bodies)
            assertTrue(game.sandboxOverlayOpen)
            assertFalse(game.sandbox!!.paused)
        }
        compose.onNodeWithTag("speed-6.0").performClick()
        compose.onNodeWithTag("close-sandbox-panel").performClick()
        compose.onNodeWithTag("speed-6.0").assertDoesNotExist()
        compose.runOnIdle { assertFalse(game.sandboxOverlayOpen); assertFalse(game.sandbox!!.paused); assertEquals(6.0, game.sandbox!!.timeScale, 0.0) }
    }

    @Test fun shipsAndRocketsCanBeChosenAndCreatedThroughRealGestures() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        listOf(BodyKind.Ship, BodyKind.Rocket).forEachIndexed { i, kind ->
            compose.onNodeWithTag("sandbox-spawn").performClick()
            compose.onNodeWithTag("close-sandbox-panel").assertDoesNotExist()
            compose.onNodeWithTag("space-scene").performTouchInput {
                val start = Offset(center.x + i * 160f, center.y)
                down(start); moveTo(start + Offset(60f, -100f)); up()
            }
            compose.runOnIdle { assertEquals(kind, game.bodies.last().kind); assertTrue(game.bodies.last().velocity.magnitude() > 0.0) }
        }
        compose.runOnIdle { assertEquals(2, game.bodies.size); assertEquals(3.0, game.bodies.last().burnRemaining, 0.0) }
    }

    @Test fun shakeModesAndIntensityAreHiddenInToolsAndPersistAfterSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prior=GameOptions(context)
        GameOptions(context).apply { shakeMode=ShakeMode.Off; shakeIntensity=1f; save() }
        val sensor = com.xekep.space.input.SpaceShake(context) {}
        try {
            val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("sandbox-shake").assertDoesNotExist()
            compose.onNodeWithTag("sandbox-tools").performClick()
            if (sensor.available) {
                compose.onNodeWithTag("shake-Classic").performScrollTo().performClick()
                compose.runOnIdle { assertEquals(ShakeMode.Classic,GameOptions(context).shakeMode) }
                compose.onNodeWithTag("shake-intensity").performScrollTo().performTouchInput { click(Offset(width*.8f,center.y)) }
                compose.runOnIdle { assertTrue(GameOptions(context).shakeIntensity > 1.5f) }
                val image=compose.onAllNodes(isRoot()).onLast().captureToImage()
                java.io.File(context.externalCacheDir,"sandbox-shake-settings.png").outputStream().use {
                    assertTrue(image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                }
                compose.onNodeWithTag("shake-Inertial").performScrollTo().performClick()
                compose.runOnIdle { assertEquals(ShakeMode.Inertial,GameOptions(context).shakeMode) }
                compose.onNodeWithTag("shake-Off").performClick()
                compose.onNodeWithTag("shake-intensity").assertIsNotEnabled()
                compose.runOnIdle { assertEquals(ShakeMode.Off,GameOptions(context).shakeMode) }
            } else listOf("Off","Classic","Inertial").forEach { compose.onNodeWithTag("shake-$it").assertIsNotEnabled() }
        } finally { sensor.close(); prior.save() }
    }
}
