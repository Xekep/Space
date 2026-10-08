package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VehicleMassUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun actualShortAndLongPressesCreateDifferentHullsInBothModes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=instrumentation.targetContext.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val oldMotion=prefs.getBoolean("motionControl",false); val oldIcons=prefs.getBoolean("largeVehicleIcons",true)
        prefs.edit().putBoolean("motionControl",false).putBoolean("largeVehicleIcons",true).commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            for (mode in AppMode.entries) for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
                compose.runOnIdle {
                    if (mode == AppMode.Arcade) game.startArcade() else { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause() }
                    game.chooseSpawnKind(kind)
                }
                compose.mainClock.advanceTimeBy(48)
                val scene=compose.onNodeWithTag("space-scene")
                scene.performTouchInput { down(center+Offset(-180f,-250f)); up() }
                val light=game.bodies.last()
                val beforeRelease=game.arcade
                scene.performTouchInput { down(center+Offset(180f,-250f)); advanceEventTime(4000); up() }
                val heavy=game.bodies.last()
                assertEquals(if (kind == BodyKind.Ship) 24.0 else 12.0,light.mass,1e-8)
                assertEquals(if (kind == BodyKind.Ship) 96.0 else 36.0,heavy.mass,1e-8)
                assertEquals(light.radius*1.18,heavy.radius.toDouble(),1e-6)
                assertEquals(VehicleHullClass.Standard,light.hullClass)
                assertEquals(VehicleHullClass.Heavy,heavy.hullClass)
                if (mode == AppMode.Arcade) {
                    val before=beforeRelease!!; val after=game.arcade!!
                    val available=minOf(before.maxEnergy,before.energy+(after.elapsed-before.elapsed)*before.energyRegen)
                    assertEquals(launchCost(heavy),available-after.energy,1e-6)
                }
                assertTrue(bodyScreenRadius(heavy,game.camera.zoom,game.density,true) > bodyScreenRadius(light,game.camera.zoom,game.density,true))
                compose.runOnIdle { game.selectBody(heavy.id) }
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithText(instrumentation.targetContext.getString(heavy.labelId())).assertIsDisplayed()
                compose.waitForIdle(); android.os.SystemClock.sleep(350)
                instrumentation.waitForIdleSync()
                File(instrumentation.targetContext.externalCacheDir,"mass-${mode.name}-${kind.name}.png").outputStream().use {
                    assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                }
            }
        } finally { prefs.edit().putBoolean("motionControl",oldMotion).putBoolean("largeVehicleIcons",oldIcons).commit() }
    }
    @Test fun legacyPreferenceDoesNotEnableTiltAndNewGameButtonsResetIt() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=prefs.getBoolean("motionControl",false)
        prefs.edit().putBoolean("motionControl",true).commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            for (mode in AppMode.entries) {
                compose.runOnIdle {
                    if (mode == AppMode.Arcade) game.startArcade() else { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause() }
                }
                compose.mainClock.advanceTimeBy(48)
                val tag=if (mode == AppMode.Arcade) "arcade-motion-control" else "sandbox-motion-control"
                compose.onNodeWithTag(tag).assertIsOff().performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag(tag).assertIsOn()
                compose.runOnIdle {
                    game.chooseSpawnKind(BodyKind.Ship)
                    game.launch(TouchPreview(Vec2(400.0,400.0),Vec2(400.0,400.0),0),0.0)
                    assertNotNull(game.controlledVehicleId)
                }
                compose.onNodeWithTag("open-menu").performClick()
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithTag("menu-primary").performScrollTo().performClick()
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithTag(tag).assertIsOn()
                compose.onNodeWithTag("open-menu").performClick()
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithTag("new-session").performScrollTo().performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("confirm-action").performClick()
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithTag(tag).assertIsOff()
                compose.runOnIdle {
                    assertFalse(game.motionSteeringEnabled); assertNull(game.controlledVehicleId)
                    assertEquals(0.0,game.cameraRotation,0.0)
                    game.chooseSpawnKind(BodyKind.Ship)
                    game.launch(TouchPreview(Vec2(5000.0,5000.0),Vec2(5000.0,5000.0),0),0.0)
                    assertNull(game.controlledVehicleId)
                }
            }
        } finally { prefs.edit().putBoolean("motionControl",previous).commit() }
    }

}
