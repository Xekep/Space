package com.xekep.space.ui.space

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.GameOptions
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class RetroUiTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val prefs get()=context.getSharedPreferences("space_options",Context.MODE_PRIVATE)
    private var original=emptyMap<String,Any?>()

    @Before fun configureRetroWithoutChangingOtherOptions() {
        original=listOf("retroConsole","flightControl","tiltSensitivity").associateWith { prefs.all[it] }
        prefs.edit().putBoolean("retroConsole",true).putString("flightControl","Tilt").putFloat("tiltSensitivity",1f).commit()
        compose.mainClock.autoAdvance=false
    }
    @After fun restoreChangedOptions() {
        prefs.edit().apply {
            original.forEach { (key,value) -> when (value) {
                null -> remove(key)
                is Boolean -> putBoolean(key,value)
                is String -> putString(key,value)
                is Float -> putFloat(key,value)
            } }
        }.commit()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun themeSwitchAndSensitivitySliderKeepAccessibleGestures() {
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade(); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("tilt-sensitivity").performTouchInput {
            down(center.copy(x=width*.35f)); moveTo(center.copy(x=width*.8f),160); up()
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(GameOptions(context).tiltSensitivity > 1.3f)
        compose.onNodeWithTag("retro-console-switch").assertIsOn().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertDoesNotExist()
        compose.onNodeWithTag("retro-console-switch").assertIsOff().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertExists()
        compose.onNodeWithTag("retro-console-switch").assertIsOn()
        assertTrue(GameOptions(context).retroConsole)
    }

    @Test fun retroPilotSliderJoystickAndZoomWorkForBothVehiclesInBothModes() {
        prefs.edit().putString("flightControl","Joystick").commit()
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startSandbox(SandboxPresetKind.Empty) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        for (mode in AppMode.entries) for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            compose.runOnIdle {
                if (mode == AppMode.Arcade) game.startArcade() else game.startSandbox(SandboxPresetKind.Empty)
                game.setMotionControlEnabled(true)
                game.chooseSpawnKind(kind)
                game.launch(TouchPreview(Vec2(4000.0,4000.0),Vec2(4000.0,4000.0),0),0.0)
                game.setPilotTargetSpeed(180.0)
            }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("pilot-speed").performTouchInput {
                down(center.copy(y=height*.8f)); moveTo(center.copy(y=height*.3f),160); up()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue(game.bodies.first { it.id == game.controlledVehicleId }.pilotTargetSpeed!! > 450) }
            val id=game.controlledVehicleId!!
            val count=game.bodies.size
            val heading=game.bodies.first { it.id == id }.heading
            compose.onNodeWithTag("flight-joystick").performTouchInput {
                down(center); moveTo(center+Offset(30*game.density,-25*game.density),64)
            }
            repeat(20) { compose.mainClock.advanceTimeByFrame() }
            compose.onNodeWithTag("flight-joystick").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle { assertTrue((game.bodies.first { it.id == id }.heading-heading).magnitude() > .1) }
            val zoom=game.camera.zoom
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(0,center-Offset(90f,0f)); down(1,center+Offset(90f,0f))
                moveTo(0,center-Offset(160f,0f)); moveTo(1,center+Offset(160f,0f)); up(0); up(1)
            }
            compose.mainClock.advanceTimeBy(48)
            compose.runOnIdle {
                assertEquals(count,game.bodies.size); assertEquals(id,game.controlledVehicleId)
                assertTrue(game.camera.zoom > zoom)
                val craft=game.bodies.first { it.id == id }
                val screen=worldToScreen(craft.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
                assertEquals(game.viewport.width/2f,screen.x,.01f); assertEquals(game.viewport.height/2f,screen.y,.01f)
            }
            compose.onNodeWithTag("pilot-fuel").assertIsDisplayed()
            shot("retro-ui-pilot-${mode.name}-${kind.name}.png")
        }
    }
}
