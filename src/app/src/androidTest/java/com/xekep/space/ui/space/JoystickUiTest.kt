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
            val id=game.controlledVehicleId!!
            val count=game.bodies.size
            val heading=game.bodies.first { body -> body.id == game.controlledVehicleId }.heading
            compose.onNodeWithTag("flight-joystick").performTouchInput {
                down(center); moveTo(center+Offset(38*game.density,-32*game.density),64)
            }
            repeat(30) { compose.mainClock.advanceTimeByFrame() }
            compose.runOnIdle {
                val craft=game.bodies.first { body -> body.id == game.controlledVehicleId }
                assertEquals(id,craft.id); assertEquals(count,game.bodies.size)
                assertTrue((craft.heading-heading).magnitude() > .1)
                assertTrue(craft.pilotTargetSpeed!! > 185)
            }
            compose.onNodeWithTag("flight-joystick").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle {
                val released=game.bodies.first { body -> body.id == game.controlledVehicleId }.heading
                game.update(.1)
                assertEquals(released.x,game.bodies.first { body -> body.id == game.controlledVehicleId }.heading.x,1e-8)
                assertEquals(released.y,game.bodies.first { body -> body.id == game.controlledVehicleId }.heading.y,1e-8)
            }
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
