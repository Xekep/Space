package com.xekep.space.ui.space

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SolarSatelliteUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun realSatelliteGestureWorksWithoutManualPauseOrZoomInTheFullSolarSystem() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle {
            game.selectBody(game.bodies.first { it.solar == SolarBody.Earth }.id)
        }
        compose.mainClock.advanceTimeByFrame()
        val parent=game.selectedBody!!
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            assertEquals(parent.position,game.orbitSource!!.position)
            assertFalse(game.sandbox!!.paused)
            assertEquals(parent.position,game.camera.center)
            assertEquals(250f,game.camera.zoom)
        }
        val point=worldToScreen(parent.position+Vec2(.35,0.0),game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.mainClock.advanceTimeByFrame()
        val satellite=game.bodies.last()
        compose.runOnIdle {
            assertEquals(18,game.bodies.size); assertNull(game.feedback)
            assertEquals(.35,(satellite.position-parent.position).magnitude(),1e-4)
            assertTrue(satellite.physicalScale)
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,"sandbox-assisted-orbit.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.runOnIdle {
            repeat(300) { game.update(1.0/60) }
            val moon=game.bodies.first { it.id == satellite.id }
            val earth=game.bodies.first { it.id == parent.id }
            assertEquals(.35,(moon.position-earth.position).magnitude(),.035)
        }
    }

    @Test fun moonOrbitIsNarrowedAndAnImpossibleExtraLevelIsRejectedClearly() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle { game.selectBody(game.bodies.first { it.solar == SolarBody.Moon }.id) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.mainClock.advanceTimeByFrame()
        val parent=game.orbitSource!!
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun place(offset: Vec2) {
            val point=worldToScreen(parent.position+offset,game.viewport,game.camera.center,game.camera.zoom)
            compose.onNodeWithTag("space-scene").performTouchInput { down(point) }
            compose.mainClock.advanceTimeBy(160)
            if (offset == Vec2(.8,0.0)) {
                compose.runOnIdle {
                    val candidate=game.previewBody(game.touchPreview!!,.16)!!
                    assertTrue((candidate.position-parent.position).magnitude() < .8)
                }
                compose.onNodeWithText(context.getString(com.xekep.space.R.string.satellite_farther)).assertDoesNotExist()
                compose.onNodeWithText(context.getString(com.xekep.space.R.string.place_satellite)).assertIsDisplayed()
                File(context.externalCacheDir,"satellite-adjusted-preview.png").outputStream().use {
                    assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                }
            }
            compose.onNodeWithTag("space-scene").performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame()
        }
        place(Vec2.Zero)
        compose.onNodeWithText(context.getString(com.xekep.space.R.string.satellite_farther)).assertIsDisplayed()
        place(Vec2(.8,0.0))
        compose.runOnIdle {
            assertEquals(18,game.bodies.size); assertNull(game.orbitSourceId); assertNull(game.feedback)
            assertTrue((game.bodies.last().position-parent.position).magnitude() < .8)
        }
        val firstSatellite=game.bodies.last()
        compose.runOnIdle { game.selectBody(firstSatellite.id) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.mainClock.advanceTimeByFrame()
        val point=worldToScreen(game.orbitSource!!.position+Vec2(.2,0.0),game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(com.xekep.space.R.string.satellite_unstable)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(18,game.bodies.size); assertEquals(firstSatellite.id,game.orbitSourceId) }
        compose.runOnIdle { game.selectBody(parent.id) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("orbit-helper").performClick()
        val before=game.bodies
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("cancel-orbit").performClick()
        compose.runOnIdle { game.update(1.0/60); assertNull(game.feedback); assertNotEquals(before,game.bodies) }
    }
}
