package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxStorage
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SolarPilotUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun solarSpeedHandleBrakesBothCraftPinchGrowsTheSymbolAndPitchChangesSize() {
        val prefs=context.getSharedPreferences("space_options",0)
        val old=prefs.getString("flightControl",null)
        prefs.edit().putString("flightControl","Joystick").commit()
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox() }
        try {
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.mainClock.advanceTimeByFrame()
            for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
                compose.runOnIdle {
                    game.startSandbox(); game.setMotionControlEnabled(true); game.chooseSpawnKind(kind)
                    game.launch(TouchPreview(Vec2(350.0,0.0),Vec2(350.0,-2000.0),0),0.0)
                }
                compose.mainClock.advanceTimeByFrame()
                val id=game.controlledVehicleId!!
                assertTrue(game.bodies.first { it.id == id }.flightSpeed() > 200)
                compose.onNodeWithTag("pilot-speed").performTouchInput {
                    down(center.copy(y=height*.10f)); moveTo(center.copy(y=height*.10f),120); up()
                }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { repeat(180) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                assertTrue(game.bodies.first { it.id == id }.flightSpeed() > 4000)
                assertEquals(5000f,compose.onNodeWithTag("pilot-speed").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive,0f)
                compose.onNodeWithTag("pilot-speed").performTouchInput {
                    down(center.copy(y=height*.1f))
                }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("pilot-speed").performTouchInput { moveTo(center.copy(y=height*.92f),120) }
                compose.mainClock.advanceTimeByFrame()
                // Exercise a held drag across the scale, not a tap with a 1.4 dp jitter.
                compose.runOnIdle { assertEquals(400.0,game.bodies.first { it.id == id }.pilotTargetSpeed!!,1.0) }
                compose.onNodeWithTag("pilot-speed").performTouchInput { up() }
                // Fixed simulation steps, no wall-clock sleeps: exercise the actual Solar world state.
                compose.runOnIdle { repeat(480) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { assertEquals(400.0,game.bodies.first { it.id == id }.flightSpeed(),3.0) }
                val initialZoom=game.pilotVisualZoom
                val initialRadius=pilotScreenRadius(game.bodies.first { it.id == id },initialZoom,game.density,true)
                compose.onNodeWithTag("space-scene").performTouchInput {
                    down(0,center-Offset(100f,0f)); down(1,center+Offset(100f,0f))
                    updatePointerTo(0,center-Offset(220f,0f)); updatePointerTo(1,center+Offset(220f,0f)); move(); up(1); up(0)
                }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val craft=game.bodies.first { it.id == id }
                    assertTrue(pilotScreenRadius(craft,game.pilotVisualZoom,game.density,true) > initialRadius*1.5f)
                    assertTrue((craft.position-game.camera.center).magnitude()*game.camera.zoom <= minOf(game.viewport.width,game.viewport.height)*.18+.01)
                }
                for (axis in listOf(-1.0,1.0)) {
                    compose.runOnIdle {
                        game.startSandbox(); game.setMotionControlEnabled(true); game.chooseSpawnKind(kind)
                        game.launch(TouchPreview(Vec2(350.0,0.0),Vec2(350.0,-2000.0),0),0.0)
                        game.setPilotTargetSpeed(400.0)
                    }
                    compose.mainClock.advanceTimeByFrame()
                    val bodyId=game.controlledVehicleId!!
                    compose.onNodeWithTag("pitch-joystick").performTouchInput {
                        down(center); moveTo(center+Offset(0f,(axis*32*game.density).toFloat()),120)
                    }
                    compose.runOnIdle { repeat(120) { game.update(1.0/60) } }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle {
                        val body=game.bodies.first { it.id == bodyId }
                        assertTrue(body.flightHeight*axis > 100)
                        assertTrue(body.pitch*axis > .5)
                        assertTrue(if (axis > 0) body.flightVisualScale() > 1.5f else body.flightVisualScale() < .5f)
                    }
                    screenshot("solar-pilot-${kind.name}-${if (axis > 0) "up" else "down"}.png")
                    compose.onNodeWithTag("pitch-joystick").performTouchInput { up() }
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNodeWithTag("pilot-exit").performClick()
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNodeWithTag("sandbox-spawn-panel").assertIsDisplayed()
                }
            }
        } finally { prefs.edit().apply { if (old == null) remove("flightControl") else putString("flightControl",old) }.commit() }
    }
    @Test fun timeButtonCyclesWhilePausedAndInspectionSpeedSurvivesSaving() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        for (expected in List(sandboxTimeScales.size) { sandboxTimeScales[(sandboxTimeScales.indexOf(1.0)+it+1)%sandboxTimeScales.size] }) {
            compose.onNodeWithTag("sandbox-time-speed").performClick()
            compose.mainClock.advanceTimeByFrame()
            assertEquals(expected,game.sandbox!!.timeScale,0.0); assertTrue(game.sandbox!!.paused)
            val snapshot=game.snapshot(0)!!; val storage=SandboxStorage(context)
            assertEquals(expected,storage.decode(storage.encode(snapshot)).timeScale,0.0)
        }
    }
}
