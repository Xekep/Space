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

class PilotZoomUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoFingerZoomKeepsThePilotAndSingleFingerStillSpawnsInBothModes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=prefs.getBoolean("motionControl",false)
        prefs.edit().putBoolean("motionControl",true).commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            for (mode in listOf(AppMode.Sandbox,AppMode.Arcade)) {
                compose.runOnIdle {
                    if (mode == AppMode.Arcade) game.startArcade()
                    game.chooseSpawnKind(if (mode == AppMode.Sandbox) BodyKind.Ship else BodyKind.Rocket)
                }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag(if (mode == AppMode.Sandbox) "sandbox-motion-control" else "arcade-motion-control").assertIsOff().performClick()
                compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(230f,-300f)) }
                compose.mainClock.advanceTimeByFrame()
                val id=game.controlledVehicleId!!
                val count=game.bodies.size
                val zoom=game.camera.zoom
                compose.onNodeWithTag("space-scene").performTouchInput {
                    val a=center+Offset(-100f,-150f); val b=center+Offset(100f,-150f)
                    down(0,a); advanceEventTime(300); down(1,b)
                    // Even tiny movements after a delayed second finger must zoom, not author a route.
                    updatePointerTo(0,a+Offset(-8f,4f)); updatePointerTo(1,b+Offset(8f,4f)); move()
                }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertNull(game.touchPreview)
                    assertTrue(game.camera.zoom > zoom)
                    assertPilotCentered(game,id)
                }
                compose.onNodeWithTag("space-scene").performTouchInput {
                    updatePointerTo(0,center+Offset(-100f,-150f)+Offset(-80f,40f))
                    updatePointerTo(1,center+Offset(100f,-150f)+Offset(160f,40f))
                    move(); up(1); up(0)
                }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals(id,game.controlledVehicleId)
                    assertEquals(count,game.bodies.size)
                    assertPilotCentered(game,id)
                    assertTrue(game.bodies.first { it.id == id }.waypoints.isEmpty())
                    val normalZoom=game.camera.zoom
                    game.transformCamera(Offset(130f,300f),Offset(80f,40f),maximumZoom(mode)/normalZoom)
                    if (mode == AppMode.Sandbox) game.toggleSandboxPause()
                    game.setSteeringInput(Vec2(.6,1.0))
                    repeat(60) {
                        game.update(1.0/60)
                        val body=game.bodies.first { it.id == id }
                        val screen=worldToScreen(body.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
                        assertTrue(kotlin.math.hypot((screen.x-game.viewport.width/2f).toDouble(),(screen.y-game.viewport.height/2f).toDouble()) <= minOf(game.viewport.width,game.viewport.height)*.18+.01)
                    }
                    game.setSteeringInput(Vec2.Zero)
                    if (mode == AppMode.Sandbox) game.toggleSandboxPause()
                    game.transformCamera(Offset.Zero,Offset.Zero,normalZoom/game.camera.zoom)
                    game.chooseSpawnKind(BodyKind.Ambient)
                }
                compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(-220f,-260f)) }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals(count+1,game.bodies.size)
                    assertEquals(id,game.controlledVehicleId)
                }
                if (mode == AppMode.Arcade) {
                    File(context.externalCacheDir,"pilot-zoom.png").outputStream().use {
                        assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                            .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                    }
                }
            }
        } finally { prefs.edit().putBoolean("motionControl",previous).commit() }
    }

    private fun assertPilotCentered(game: SpaceGameState,id: Long) {
        val body=game.bodies.first { it.id == id }
        val screen=worldToScreen(body.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
        assertTrue(kotlin.math.hypot((screen.x-game.viewport.width/2f).toDouble(),(screen.y-game.viewport.height/2f).toDouble()) <= minOf(game.viewport.width,game.viewport.height)*.18+.01)
    }
}
