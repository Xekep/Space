package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NewGameplayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exhaustedRocketHasNoFlameThenDriftsAndExplodes() {
        compose.mainClock.autoAdvance=false
        val powered=CelestialBody(1,Vec2(-160.0,0.0),Vec2(0.0,-100.0),12.0,6f,
            androidx.compose.ui.graphics.Color.White,BodyKind.Rocket)
        val empty=powered.copy(id=2,position=Vec2(160.0,0.0),velocity=Vec2(100.0,0.0),
            fuelRemaining=0.0,driftRemaining=3.25,trail=listOf(Vec2(160.0,0.0)))
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(powered,empty),Vec2.Zero,1f,0.0,123L,
                paused=true,preset=SandboxPresetKind.Empty))
        }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        val bitmap=compose.onNodeWithTag("space-scene").captureToImage().asAndroidBitmap()
        val radius=bodyScreenRadius(powered,1f,compose.density.density,game.largeVehicleIcons)
        val y=(bitmap.height/2f+radius*1.4f).toInt()
        val flame=bitmap.getPixel((bitmap.width/2f-160f).toInt(),y)
        val dark=bitmap.getPixel((bitmap.width/2f+160f).toInt(),y)
        assertTrue(android.graphics.Color.red(flame) > 230 && android.graphics.Color.green(flame) > 100)
        assertTrue(android.graphics.Color.red(dark) < 100 && android.graphics.Color.green(dark) < 100)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir,"rocket-powered-coasting.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.runOnIdle {
            game.toggleSandboxPause()
            repeat(20) { game.update(.1) }
            val coasting=game.bodies.first { it.id == 2L }
            assertTrue((coasting.velocity-empty.velocity).magnitude() < 1e-6)
            assertEquals(1.25,coasting.driftRemaining,1e-6)
            repeat(13) { game.update(.1) }
            assertTrue(game.bodies.none { it.id == 2L }); assertTrue(game.explosions.isNotEmpty())
        }
    }

    @Test fun menuVehicleIconSwitchChangesBothCraftAndSavesTheChoice() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val prior=prefs.getBoolean("largeVehicleIcons",true)
        prefs.edit().putBoolean("largeVehicleIcons",true).commit()
        try {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.ClassicOrbits); toggleSandboxPause(); openMenu() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("large-vehicle-icons").performScrollTo().assertIsOn().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("large-vehicle-icons").assertIsOff()
            compose.runOnIdle {
                assertFalse(game.largeVehicleIcons)
                assertFalse(com.xekep.space.storage.GameOptions(context).largeVehicleIcons)
                for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
                    val body=CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,6f,androidx.compose.ui.graphics.Color.Cyan,kind)
                    assertTrue(bodyScreenRadius(body,1f,2f,true) > bodyScreenRadius(body,1f,2f,false))
                    assertEquals(6f,body.radius,0f)
                }
            }
            compose.onNodeWithTag("large-vehicle-icons").performClick().assertIsOn()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue(game.largeVehicleIcons) }
        } finally { prefs.edit().putBoolean("largeVehicleIcons",prior).commit() }
    }

    @Test fun pilotMetersShowSpeedAndSlowlyDrainingFuelInBothModesAndDisappearAfterExpiry() {
        compose.mainClock.autoAdvance=false
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=prefs.getBoolean("motionControl",false)
        prefs.edit().putBoolean("motionControl",true).commit()
        try {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("pilot-hud").assertDoesNotExist()
            compose.onNodeWithTag("sandbox-spawn").performClick()
            compose.onNodeWithTag("space-scene").performTouchInput { click(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("pilot-speed").assertIsDisplayed()
            compose.onNodeWithTag("pilot-fuel").assertIsDisplayed()
            compose.runOnIdle {
                game.toggleSandboxPause(); repeat(600) { game.update(.1) }; game.toggleSandboxPause()
                assertEquals(120.0,game.bodies.single().fuelRemaining,1e-4)
            }
            compose.mainClock.advanceTimeByFrame()
            java.io.File(context.externalCacheDir,"pilot-fuel-speed.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            compose.runOnIdle {
                game.toggleSandboxPause()
                var ticks=0
                while (game.controlledVehicleId != null && ticks++<1300) game.update(.1)
                assertTrue(game.bodies.isEmpty()); assertTrue(game.explosions.isNotEmpty())
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("pilot-hud").assertDoesNotExist()
            compose.runOnIdle { game.startArcade(); game.chooseSpawnKind(BodyKind.Rocket) }
            compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(230f,-300f)) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("pilot-speed").assertIsDisplayed()
            compose.onNodeWithTag("pilot-fuel").assertIsDisplayed()
            compose.runOnIdle { assertTrue(game.bodies.last().fuelRemaining in 119.8..120.0) }
        } finally { prefs.edit().putBoolean("motionControl",previous).commit() }
    }

    @Test fun sandboxCycleCreatesStarsAndBlackHolesWithoutOpeningMenus() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        repeat(3) { compose.onNodeWithTag("sandbox-spawn").performClick() }
        listOf(BodyKind.Star,BodyKind.BlackHole).forEachIndexed { i,kind ->
            if (i > 0) compose.onNodeWithTag("sandbox-spawn").performClick()
            compose.onNodeWithTag("close-sandbox-panel").assertDoesNotExist()
            compose.onNodeWithTag("space-scene").performTouchInput {
                val point=Offset(center.x+(i*2-1)*180f,center.y-200f)
                down(point); advanceEventTime(600); up()
            }
            compose.runOnIdle { assertEquals(kind,game.bodies.last().kind); assertTrue(game.bodies.last().mass >= 6000.0) }
            compose.mainClock.advanceTimeByFrame()
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir,"celestial-spawns.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.runOnIdle { assertEquals(2,game.bodies.size) }
        compose.onNodeWithTag("sandbox-spawn").performClick()
        compose.runOnIdle { assertEquals(BodyKind.Ambient,game.spawnKind) }
    }

    @Test fun arcadeButtonCyclesAndRealGesturesLaunchAllThreeObjectsWithoutMenus() {
        compose.mainClock.autoAdvance = false
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        listOf(BodyKind.Ship, BodyKind.Rocket, BodyKind.Player).forEachIndexed { i, kind ->
            compose.onNodeWithTag("arcade-cycle-spawn").performClick()
            compose.onNodeWithTag("close-sandbox-panel").assertDoesNotExist()
            compose.onNodeWithTag("space-scene").performTouchInput {
                val start = Offset(center.x + (i - 1) * 100f, center.y - 160f)
                down(start); moveTo(start + Offset(30f, -90f)); up()
            }
            compose.runOnIdle { assertEquals(kind, game.bodies.last().kind) }
        }
        compose.runOnIdle { assertEquals(3, game.arcade!!.launches); assertTrue(game.arcade!!.energy >= 0) }
    }

    @Test fun solarNavigationFocusesEarthAndItsMoonThenReturnsToOverview() {
        val game = SpaceGameState().apply { resize(IntSize(1080, 2340)); startSandbox(); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        val point=worldToScreen(earth.position,game.viewport,game.camera.center,game.camera.zoom)
        compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
        compose.onNodeWithTag("body-toolbar").assertExists()
        compose.onNodeWithTag("open-body-tools").assertDoesNotExist()
        compose.onNodeWithTag("focus-body").performClick()
        compose.onNodeWithTag("follow-body").performClick()
        compose.onNodeWithTag("close-sandbox-panel").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(SolarBody.Earth, game.selectedBody!!.solar); assertTrue(game.following)
            assertTrue(game.camera.zoom > 20); assertTrue(game.bodies.any { it.solar == SolarBody.Moon })
        }
        compose.onNodeWithTag("fit-system").performClick()
        compose.runOnIdle { assertFalse(game.following); assertTrue(game.camera.zoom < .05) }
    }

    @Test fun explosionRendersThenItsDebrisDisappearsWithoutLeavingBodies() {
        compose.mainClock.autoAdvance = false
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause(); chooseSpawnKind(BodyKind.Rocket) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle {
            repeat(2) { game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.0) }
            game.toggleSandboxPause(); game.update(.20); game.toggleSandboxPause()
            assertTrue(game.bodies.isEmpty()); assertEquals(1, game.explosions.size)
            assertEquals(24, game.explosions.single().particles.size)
        }
        compose.mainClock.advanceTimeByFrame()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir, "vehicle-explosion.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.runOnIdle {
            game.toggleSandboxPause(); repeat(80) { game.update(1.0 / 60) }; game.toggleSandboxPause()
            assertTrue(game.explosions.isEmpty()); assertTrue(game.bodies.isEmpty())
        }
    }

    @Test fun thirdPresetButtonRestoresTheClassicPlayground() {
        val game = SpaceGameState()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("preset-SolarSystem").assertExists()
        compose.onNodeWithTag("preset-BinaryStars").assertExists()
        compose.onNodeWithTag("preset-ClassicOrbits").performScrollTo().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir, "sandbox-presets.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle {
            assertEquals(SandboxPresetKind.ClassicOrbits, game.sandbox!!.preset)
            assertEquals(9, game.bodies.size); assertEquals(12000.0, game.bodies.first().mass, 0.0)
        }
    }

    @Test fun compactCounterAndTiltOptionAreAvailableInSandboxTools() {
        compose.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("space_options", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getBoolean("motionControl", false)
        preferences.edit().putBoolean("motionControl", false).commit()
        try {
            val game = SpaceGameState().apply { resize(IntSize(1080,2340)); startSandbox(); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("object-counter").assertTextEquals("17/1000")
            compose.onNodeWithTag("sandbox-motion-control").assertIsOff().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("sandbox-motion-control").assertIsOn()
            compose.onNodeWithTag("sandbox-tools").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("focus-Earth").assertDoesNotExist()
            compose.runOnIdle { assertTrue(preferences.getBoolean("motionControl", false)) }
            compose.onNodeWithTag("close-sandbox-panel").performClick()
            compose.runOnIdle {
                game.focusSolar(SolarBody.Saturn)
            }
            compose.mainClock.advanceTimeByFrame()
            java.io.File(context.externalCacheDir, "solar-saturn.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
        } finally { preferences.edit().putBoolean("motionControl",previous).commit() }
    }

    @Test fun lastLaunchedCraftGetsPilotIndicatorAndCaptionsFadeAfterOneMinute() {
        compose.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous = preferences.getBoolean("motionControl",false)
        preferences.edit().putBoolean("motionControl",true).commit()
        try {
            val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startSandbox(); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("sandbox-spawn").performClick()
            compose.onNodeWithTag("space-scene").performTouchInput {
                val point=Offset(center.x + 200f,center.y - 400f)
                down(point); advanceEventTime(650); up()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(BodyKind.Ship,game.bodies.last().kind)
                assertEquals(game.bodies.last().id,game.controlledVehicleId)
                game.toggleSandboxPause(); game.setSteeringInput(Vec2(1.0,0.0)); game.update(.1); game.toggleSandboxPause()
                assertTrue(game.bodies.last().heading.x > .3)
                repeat(600) { game.update(.1) }
                assertEquals(0f,solarLabelAlpha(game.presentationAge),0f)
            }
            compose.mainClock.advanceTimeByFrame()
            java.io.File(context.externalCacheDir,"solar-pilot-no-labels.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
        } finally { preferences.edit().putBoolean("motionControl",previous).commit() }
    }

    @Test fun rotatedPilotCameraKeepsPlanetSelectionAccurateAndReturnsWhenSwitchedOff() {
        compose.mainClock.autoAdvance=false
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=preferences.getBoolean("motionControl",false)
        preferences.edit().putBoolean("motionControl",true).commit()
        try {
            val game=SpaceGameState().apply {
                startSandbox(SandboxPresetKind.Empty)
                launch(TouchPreview(Vec2(-150.0,-50.0),Vec2(-150.0,-50.0),0),0.0)
                chooseSpawnKind(BodyKind.Ship); launch(TouchPreview(Vec2.Zero,Vec2.Zero,0),0.0)
            }
            val planet=game.bodies.first().id
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.runOnIdle {
                game.setSteeringInput(Vec2(1.0,0.0)); repeat(30) { game.update(1.0/60) }
                game.setSteeringInput(Vec2.Zero); repeat(90) { game.update(1.0/60) }; game.toggleSandboxPause()
                assertTrue(kotlin.math.abs(game.cameraRotation) > 1)
                game.chooseSpawnKind(BodyKind.Ambient)
            }
            compose.mainClock.advanceTimeByFrame()
            val body=game.bodies.first { it.id == planet }
            val point=worldToScreen(body.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
            compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(planet,game.selectedBodyId) }
            compose.onNodeWithTag("body-toolbar").assertIsDisplayed()
            java.io.File(context.externalCacheDir,"pilot-rotated-camera.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            compose.onNodeWithTag("sandbox-motion-control").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertFalse(game.motionSteeringEnabled); assertNull(game.controlledVehicleId)
                repeat(100) { game.update(1.0/60) }
                assertEquals(0.0,game.cameraRotation,0.0)
            }
        } finally { preferences.edit().putBoolean("motionControl",previous).commit() }
    }

    @Test fun secondFingerTapsAuthorTheRouteAndMovingBothFingersStillControlsTheCamera() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause(); chooseSpawnKind(BodyKind.Ship) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("space-scene").performTouchInput {
            val first=center-Offset(120f,0f)
            down(0,first); advanceEventTime(300)
            down(1,center+Offset(180f,-180f)); up(1)
            down(1,center+Offset(180f,150f)); up(1)
        }
        compose.runOnIdle { assertEquals(2,game.touchPreview!!.waypoints.size); assertTrue(game.bodies.isEmpty()) }
        compose.mainClock.advanceTimeByFrame()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir,"route-preview.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.onNodeWithTag("space-scene").performTouchInput { up(0) }
        compose.runOnIdle { assertEquals(2,game.bodies.single().waypoints.size); assertNull(game.touchPreview) }
        compose.runOnIdle { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause(); game.chooseSpawnKind(BodyKind.Rocket) }
        compose.onNodeWithTag("space-scene").performTouchInput {
            val first=center-Offset(120f,0f); val second=center+Offset(120f,0f)
            down(0,first); advanceEventTime(300); down(1,second)
            updatePointerTo(0,first+Offset(140f,60f)); updatePointerTo(1,second+Offset(140f,60f)); move()
            up(1); up(0)
        }
        compose.runOnIdle { assertTrue(game.bodies.isEmpty()); assertTrue(game.camera.center.magnitude() > 50) }
    }

    @Test fun motionIconsToggleTheSameOptionInBothModes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=preferences.getBoolean("motionControl",false)
        preferences.edit().putBoolean("motionControl",false).commit()
        try {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); toggleSandboxPause() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.onNodeWithTag("sandbox-motion-control").assertIsOff().performClick()
            compose.onNodeWithTag("sandbox-motion-control").assertIsOn()
            compose.runOnIdle { game.enterMode(AppMode.Arcade) }
            compose.onNodeWithTag("arcade-motion-control").assertIsOn().performClick()
            compose.onNodeWithTag("arcade-motion-control").assertIsOff()
            compose.runOnIdle { game.enterMode(AppMode.Sandbox) }
            compose.onNodeWithTag("sandbox-motion-control").assertIsOff()
            compose.runOnIdle { assertFalse(preferences.getBoolean("motionControl",true)) }
        } finally { preferences.edit().putBoolean("motionControl",previous).commit() }
    }
}
