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

class FollowingZoomUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun offCentreTwoFingerZoomKeepsFollowingBeforeDuringAndAfterSatellitePlacement() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle {
            game.selectBody(game.bodies.first { it.solar == SolarBody.Earth }.id)
            game.followSelected(); game.focusSelected()
        }
        compose.mainClock.advanceTimeByFrame()
        val id=game.selectedBodyId!!
        fun pinch() {
            val count=game.bodies.size
            compose.onNodeWithTag("space-scene").performTouchInput {
                val a=center+Offset(-150f,-230f); val b=center+Offset(150f,-230f)
                down(0,a); down(1,b)
                updatePointerTo(0,a+Offset(70f,40f)); updatePointerTo(1,b+Offset(-40f,40f)); move()
                up(1); up(0)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertTrue(game.following); assertEquals(id,game.cameraTarget!!.id)
                assertEquals(game.cameraTarget!!.position,game.camera.center)
                assertEquals(count,game.bodies.size)
            }
        }
        pinch()
        compose.onNodeWithTag("orbit-helper").performClick()
        compose.mainClock.advanceTimeByFrame()
        pinch()
        val earth=game.orbitSource!!
        val point=worldToScreen(earth.position+Vec2(.35,0.0),game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(SolarSystem.BODY_COUNT+1,game.bodies.size); assertEquals(id,game.selectedBodyId) }
        pinch()
        compose.runOnIdle {
            game.toggleSandboxPause()
            repeat(300) { game.update(1.0/60) }
            assertTrue(game.following); assertEquals(id,game.selectedBodyId)
            assertEquals(game.selectedBody!!.position,game.camera.center)
        }
        compose.mainClock.advanceTimeByFrame()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,"following-satellite-zoom.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
}
