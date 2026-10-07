package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.xekep.space.R
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpaceInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun gestureScene(): SpaceGameState {
        val game = SpaceGameState()
        game.startSandbox(SandboxPresetKind.Empty)
        game.toggleSandboxPause()
        compose.setContent {
            Canvas(Modifier.fillMaxSize().testTag("scene").onSizeChanged(game::resize).spaceGestures(game, game.hasSession)) {}
        }
        compose.waitForIdle()
        return game
    }

    @Test fun oneFingerCreatesABodyWithoutPanning() {
        val game = gestureScene()
        val camera = game.camera
        compose.onNodeWithTag("scene").performTouchInput {
            down(Offset(width * 0.4f, height * 0.4f))
            moveTo(Offset(width * 0.6f, height * 0.4f))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, game.bodies.size)
            assertEquals(camera, game.camera)
        }
    }

    @Test fun twoFingersPanWithoutCreatingABodyEvenWhenReleasedSeparately() {
        val game = gestureScene()
        compose.onNodeWithTag("scene").performTouchInput {
            val first = Offset(width * 0.3f, height * 0.4f)
            val second = Offset(width * 0.7f, height * 0.4f)
            down(0, first)
            down(1, second)
            updatePointerTo(0, first + Offset(80f, 60f))
            updatePointerTo(1, second + Offset(80f, 60f))
            move()
            up(1)
            moveTo(0, first + Offset(100f, 70f))
            up(0)
        }
        compose.runOnIdle {
            assertEquals(-80.0, game.camera.center.x, 0.1)
            assertEquals(-60.0, game.camera.center.y, 0.1)
            assertEquals(1f, game.camera.zoom, 0.001f)
            assertTrue(game.bodies.isEmpty())
            assertNull(game.touchPreview)
        }
        // The next independent one-finger gesture should still launch normally.
        compose.onNodeWithTag("scene").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, game.bodies.size) }
    }

    @Test fun pinchZoomWorksAndDoesNotLaunchOnRelease() {
        val game = gestureScene()
        compose.onNodeWithTag("scene").performTouchInput {
            val middle = center
            val gap = width * 0.15f
            down(0, middle - Offset(gap, 0f))
            down(1, middle + Offset(gap, 0f))
            updatePointerTo(0, middle - Offset(gap * 2f, 0f))
            updatePointerTo(1, middle + Offset(gap * 2f, 0f))
            move()
            up(0)
            up(1)
        }
        compose.runOnIdle {
            assertEquals(2f, game.camera.zoom, 0.01f)
            assertEquals(0.0, game.camera.center.magnitude(), 0.1)
            assertTrue(game.bodies.isEmpty())
        }
    }

    @Test fun creatingAfterTwoFingerTravelWorksBeyondTheOriginalArena() {
        val game = SpaceGameState()
        compose.setContent {
            Canvas(Modifier.fillMaxSize().testTag("scene").onSizeChanged(game::resize).spaceGestures(game, game.hasSession)) {}
        }
        compose.runOnIdle { game.startArcade() }
        val before = game.bodies.size
        repeat(4) {
            compose.onNodeWithTag("scene").performTouchInput {
                val first = Offset(width * 0.15f, height * 0.4f)
                val second = Offset(width * 0.4f, height * 0.4f)
                down(0, first)
                down(1, second)
                updatePointerTo(0, first + Offset(width * 0.45f, 0f))
                updatePointerTo(1, second + Offset(width * 0.45f, 0f))
                move()
                up(1)
                up(0)
            }
        }
        compose.runOnIdle {
            assertTrue(game.camera.center.x < -CullMargin)
            assertEquals(before, game.bodies.size)
        }
        compose.onNodeWithTag("scene").performTouchInput { click(center) }
        compose.runOnIdle {
            val created = game.bodies.last()
            assertEquals(before + 1, game.bodies.size)
            assertEquals(game.camera.center.x, created.position.x, 0.1)
            assertEquals(game.camera.center.y, created.position.y, 0.1)
            repeat(60) { game.update(1.0 / 60.0) }
            assertTrue(game.bodies.any { it.id == created.id })
            val point = worldToScreen(game.bodies.first { it.id == created.id }.position, game.viewport, game.camera.center, game.camera.zoom)
            assertEquals(game.viewport.width / 2f, point.x, 4f)
            assertEquals(game.viewport.height / 2f, point.y, 4f)
        }
    }

    @Test fun modeSelectionStartsSolarSystemAndKeepsItWhenSwitchingBack() {
        val game = SpaceGameState()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.universe_awaits)).assertDoesNotExist()
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("preset-SolarSystem").performScrollTo().performClick()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.onNodeWithTag("sandbox-pause").performClick()
        compose.runOnIdle { assertEquals(com.xekep.space.sim.SolarBody.entries.size, game.bodies.size); assertTrue(game.sandbox!!.paused) }
        compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
        val sandbox = game.sandbox!!
        compose.onNodeWithTag("open-menu").performClick()
        compose.onNodeWithTag("mode-Arcade").performClick()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.onNodeWithTag("open-menu").performClick()
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle { assertEquals(sandbox, game.sandbox) }
    }

    @Test fun startingANewUniverseRequiresConfirmation() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val before = game.sandbox
        compose.onNodeWithTag("preset-SolarSystem").performScrollTo().performClick()
        compose.onNodeWithTag("new-session").performScrollTo().performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.keep_current)).performClick()
        compose.runOnIdle { assertEquals(before, game.sandbox) }
        compose.onNodeWithTag("new-session").performClick()
        compose.onNodeWithTag("confirm-action").performClick()
        compose.runOnIdle { assertEquals(com.xekep.space.sim.SolarBody.entries.size, game.bodies.size); assertEquals(SandboxPresetKind.SolarSystem, game.sandbox!!.preset) }
    }
}
