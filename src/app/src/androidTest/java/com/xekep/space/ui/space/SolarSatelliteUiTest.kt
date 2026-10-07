package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
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

    @Test fun realSatelliteGestureWorksAtDeepZoomInTheFullSolarSystem() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle {
            game.focusSolar(SolarBody.Earth)
            game.transformCamera(Offset(game.viewport.width/2f,game.viewport.height/2f),Offset.Zero,250f/game.camera.zoom)
        }
        compose.mainClock.advanceTimeByFrame()
        val parent=game.selectedBody!!
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.mainClock.advanceTimeByFrame()
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
            game.toggleSandboxPause()
            repeat(300) { game.update(1.0/60) }
            val moon=game.bodies.first { it.id == satellite.id }
            val earth=game.bodies.first { it.id == parent.id }
            assertEquals(.35,(moon.position-earth.position).magnitude(),.035)
        }
    }
}
