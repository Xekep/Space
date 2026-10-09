package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.input.FlightControlMode
import com.xekep.space.sim.*
import com.xekep.space.storage.GameOptions
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class JoystickUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun withOriginalOptions(block: () -> Unit) {
        val prefs=context.getSharedPreferences("space_options",0)
        val oldMode=prefs.getString("flightControl",null)
        val hadSensitivity=prefs.contains("tiltSensitivity")
        val oldSensitivity=prefs.getFloat("tiltSensitivity",1f)
        prefs.edit().putString("flightControl","Tilt").putFloat("tiltSensitivity",1f).commit()
        try { block() } finally {
            prefs.edit().apply {
                if (oldMode == null) remove("flightControl") else putString("flightControl",oldMode)
                if (hadSensitivity) putFloat("tiltSensitivity",oldSensitivity) else remove("tiltSensitivity")
            }.commit()
        }
    }
    @Test fun settingsSelectJoystickAndRealDraggingSteersAcceleratesAndReleasesInBothModes() = withOriginalOptions {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade(); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("flight-control-Joystick").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("flight-control-Joystick").assertIsSelected()
        assertEquals(FlightControlMode.Joystick,GameOptions(context).flightControl)
        compose.onNodeWithTag("close-menu-panel").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("menu-primary").performClick()
        compose.mainClock.advanceTimeBy(48)
        for (mode in AppMode.entries) for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            compose.runOnIdle {
                if (mode == AppMode.Arcade) game.startArcade() else game.startSandbox(SandboxPresetKind.Empty)
            }
            compose.mainClock.advanceTimeBy(48)
            val tag=if (mode == AppMode.Arcade) "arcade-motion-control" else "sandbox-motion-control"
            compose.onNodeWithTag(tag).assertIsOff().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                game.chooseSpawnKind(kind)
                game.launch(TouchPreview(Vec2(4000.0,4000.0),Vec2(4000.0,4000.0),0),0.0)
                game.setPilotTargetSpeed(180.0)
            }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("flight-joystick").assertIsDisplayed()
            compose.onNodeWithTag("pilot-exit").assertIsDisplayed()
            compose.onNodeWithTag(if (mode == AppMode.Sandbox) "sandbox-spawn-panel" else "arcade-spawn-panel").assertDoesNotExist()
            compose.onNodeWithTag("arcade-launch-energy").assertDoesNotExist()
            val id=game.controlledVehicleId!!
            val count=game.bodies.size
            val heading=game.bodies.first { body -> body.id == game.controlledVehicleId }.heading
            compose.onNodeWithTag("thrust-joystick").assertIsDisplayed()
            val hud=compose.onNodeWithTag("pilot-hud").fetchSemanticsNode().boundsInRoot
            val left=compose.onNodeWithTag("thrust-joystick").fetchSemanticsNode().boundsInRoot.center-hud.topLeft
            val right=compose.onNodeWithTag("flight-joystick").fetchSemanticsNode().boundsInRoot.center-hud.topLeft
            compose.onNodeWithTag("pilot-hud").performTouchInput {
                down(0,left); down(1,right)
                moveTo(0,left-Offset(0f,20*game.density),64)
                moveTo(1,right+Offset(28*game.density,-28*game.density),64)
            }
            repeat(30) { compose.mainClock.advanceTimeByFrame() }
            compose.runOnIdle {
                val craft=game.bodies.first { body -> body.id == game.controlledVehicleId }
                assertEquals(id,craft.id); assertEquals(count,game.bodies.size)
                assertTrue((craft.heading-heading).magnitude() > .1)
                assertTrue(craft.pilotTargetSpeed!! > 650)
                assertTrue(craft.flightHeight > 5); assertTrue(craft.pitch > .1)
            }
            // Regrip the throttle while the steering finger stays down.
            compose.onNodeWithTag("pilot-hud").performTouchInput { up(0) }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("pilot-hud").performTouchInput { down(0,left+Offset(0f,16*game.density)) }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle { assertEquals(225.0,game.bodies.first { it.id == id }.pilotTargetSpeed!!,1.0) }
            compose.onNodeWithTag("pilot-hud").performTouchInput { moveTo(0,left-Offset(0f,20*game.density),64); up(0) }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle { assertTrue(game.bodies.first { it.id == id }.pilotTargetSpeed!! > 650) }
            compose.onNodeWithTag("pilot-hud").performTouchInput { up(1) }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle {
                val released=game.bodies.first { body -> body.id == game.controlledVehicleId }.heading
                game.update(.1)
                assertEquals(released.x,game.bodies.first { body -> body.id == game.controlledVehicleId }.heading.x,1e-8)
                assertEquals(released.y,game.bodies.first { body -> body.id == game.controlledVehicleId }.heading.y,1e-8)
            }
            // Right-stick vertical motion changes pitch, never the latched thrust setting.
            val throttle=game.bodies.first { it.id == id }.pilotTargetSpeed!!
            compose.onNodeWithTag("flight-joystick").performTouchInput {
                down(center); moveTo(center+Offset(0f,32*game.density),64)
            }
            repeat(12) { compose.mainClock.advanceTimeByFrame() }
            compose.runOnIdle { assertEquals(throttle,game.bodies.first { it.id == id }.pilotTargetSpeed!!,1e-6) }
            compose.onNodeWithTag("flight-joystick").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("pilot-speed").performTouchInput {
                down(center.copy(y=height*.8f)); moveTo(center.copy(y=height*.35f),120)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(585.0,game.bodies.first { it.id == id }.pilotTargetSpeed!!,1.0)
                assertEquals(count,game.bodies.size)
            }
            compose.onNodeWithTag("pilot-speed").performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame()
            val zoom=game.camera.zoom
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(0,center-Offset(100f,0f)); down(1,center+Offset(100f,0f))
                moveTo(0,center-Offset(150f,0f)); moveTo(1,center+Offset(150f,0f)); up(0); up(1)
            }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle {
                assertTrue(game.camera.zoom > zoom); assertEquals(count,game.bodies.size)
                assertEquals(id,game.controlledVehicleId)
                val craft=game.bodies.first { body -> body.id == game.controlledVehicleId }
                val screen=worldToScreen(craft.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
                assertEquals(game.viewport.width/2f,screen.x,.01f); assertEquals(game.viewport.height/2f,screen.y,.01f)
            }
            compose.waitForIdle(); android.os.SystemClock.sleep(200); instrumentation.waitForIdleSync()
            File(context.externalCacheDir,"joystick-${mode.name}-${kind.name}.png").outputStream().use {
                assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            compose.onNodeWithTag("thrust-joystick").performTouchInput {
                down(center); moveTo(center+Offset(0f,32*game.density),64); up()
            }
            compose.mainClock.advanceTimeBy(48)
            val stopped=game.bodies.first { it.id == id }
            assertEquals(0.0,stopped.pilotTargetSpeed!!,1e-5); assertFalse(stopped.enginePowered)
            compose.onNodeWithTag("flight-joystick").performTouchInput {
                down(center); moveTo(center+Offset(28*game.density,-28*game.density),64)
            }
            repeat(12) { compose.mainClock.advanceTimeByFrame() }
            compose.runOnIdle {
                val drift=game.bodies.first { it.id == id }
                assertEquals(stopped.heading,drift.heading); assertEquals(stopped.pitch,drift.pitch,1e-8)
                assertEquals(stopped.fuelRemaining,drift.fuelRemaining,1e-8)
            }
            compose.onNodeWithTag("flight-joystick").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("pilot-exit").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("pilot-hud").assertDoesNotExist()
            compose.onNodeWithTag(if (mode == AppMode.Sandbox) "sandbox-spawn-panel" else "arcade-spawn-panel").assertIsDisplayed()
            if (mode == AppMode.Arcade) compose.onNodeWithTag("arcade-launch-energy").assertIsDisplayed()
            assertFalse(game.motionSteeringEnabled)
        }
    }
    @Test fun tiltSensitivityPersistsAndTheSliderHidesForJoystick() = withOriginalOptions {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade(); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("tilt-sensitivity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1.5f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1.5f,GameOptions(context).tiltSensitivity,0f)
        compose.onNodeWithTag("flight-control-Joystick").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tilt-sensitivity").assertDoesNotExist()
        compose.onNodeWithTag("flight-control-Tilt").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tilt-sensitivity").assertExists()
        assertEquals(FlightControlMode.Tilt,GameOptions(context).flightControl)
        assertFalse(game.motionSteeringEnabled)
    }
}
