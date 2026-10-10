package com.xekep.space.ui.space

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.R
import com.xekep.space.sim.*
import com.xekep.space.storage.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MenuRedesignUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun mainMenuUsesHorizontalTabsAndShowsSettingsAndDetailsOnlyWhenOpened() {
        compose.setContent { SpaceTheme { SpaceSceneRoot(SpaceGameState()) } }
        val arcade=compose.onNodeWithTag("mode-Arcade").fetchSemanticsNode().boundsInRoot
        val sandbox=compose.onNodeWithTag("mode-Sandbox").fetchSemanticsNode().boundsInRoot
        assertEquals(arcade.top,sandbox.top,1f); assertTrue(arcade.right <= sandbox.left)
        compose.onNodeWithTag("ambient-music-switch").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.universe_awaits)).assertDoesNotExist()
        compose.onNodeWithTag("menu-primary").assertIsDisplayed()
        compose.onNodeWithTag("menu-language").assertIsDisplayed()
        shot("menu-redesign-arcade.png")
        compose.onNodeWithTag("menu-info").performClick()
        compose.onNodeWithText(context.getString(R.string.difficulty_details,4,"1.0")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("ambient-music-switch").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("large-vehicle-icons").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("practice-controls").performScrollTo().assertIsDisplayed()
        shot("menu-redesign-settings.png")
        compose.onNodeWithTag("close-menu-panel").performClick()
        compose.onNodeWithTag("ambient-music-switch").assertDoesNotExist()
    }
    @Test fun fourthPresetCreatesFiveHundredBodiesAndReopeningKeepsTheChoice() {
        val game=SpaceGameState()
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("mode-Sandbox").performClick()
        compose.onNodeWithTag("open-worlds").performClick()
        compose.onNodeWithTag("save-slot-1").performScrollTo().assertIsNotEnabled()
        if (SandboxStorage(context).summaries()[0] == null) compose.onNodeWithTag("load-slot-1").assertIsNotEnabled()
        else compose.onNodeWithTag("load-slot-1").assertIsEnabled()
        compose.onNodeWithTag("close-menu-panel").performClick()
        compose.onNodeWithTag("preset-RandomSystems").performScrollTo().performClick().assertIsSelected()
        shot("menu-redesign-sandbox.png")
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle {
            assertEquals(500,game.bodies.size); assertTrue(game.bodies.count { it.kind == BodyKind.Star } > 400); assertEquals(1,game.bodies.count { it.kind == BodyKind.BlackHole })
            assertEquals(SandboxPresetKind.RandomSystems,game.sandbox!!.preset); assertFalse(game.menuOpen)
            game.openMenu()
        }
        compose.onNodeWithTag("preset-RandomSystems").assertIsSelected()
        val before=game.sandbox!!
        compose.onNodeWithTag("menu-primary").performClick()
        compose.runOnIdle { assertEquals(before,game.sandbox) }
    }
    @Test fun worldsPanelSavesLoadsRenamesAndKeepsConfirmationForReplacingAWorld() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars,"Test"); toggleSandboxPause(); openMenu() }
        var summaries by mutableStateOf<List<SandboxSlotSummary?>>(listOf(null,null,null))
        var stored: SandboxSnapshot?=null
        var saves=0; var imports=0; var exports=0
        compose.setContent { SpaceTheme { SpaceMenu(game,summaries,null,
            onSaveSlot={ slot -> stored=game.snapshot(0); summaries=summaries.toMutableList().also { it[slot-1]=SandboxSlotSummary(slot,stored!!.bodies.size,0,stored!!.name,stored!!.bodies) }; saves++ },
            onLoadSlot={ game.loadSandbox(stored!!) },onImport={ imports++ },onExport={ exports++ }) } }
        compose.onNodeWithTag("save-slot-1").assertDoesNotExist()
        compose.onNodeWithTag("open-worlds").performClick()
        compose.onNodeWithTag("save-slot-1").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,saves); assertEquals(game.bodies,stored!!.bodies) }
        compose.onNodeWithTag("save-slot-1").performClick()
        compose.onNodeWithText(context.getString(R.string.keep_current)).performClick()
        compose.runOnIdle { assertEquals(1,saves) }
        compose.onNodeWithTag("rename-world").performScrollTo().performClick()
        compose.onNodeWithTag("world-name").performTextReplacement("Renamed")
        compose.onNodeWithTag("save-world-name").performClick()
        compose.runOnIdle { assertEquals("Renamed",game.sandbox!!.name) }
        shot("menu-redesign-worlds.png")
        compose.onNodeWithTag("load-slot-1").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-action").performClick()
        compose.runOnIdle { assertEquals("Test",game.sandbox!!.name); assertEquals(stored!!.bodies,game.bodies) }
        compose.onNodeWithTag("export-world").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,exports) }
        compose.onNodeWithTag("open-worlds").performClick()
        compose.onNodeWithTag("import-world").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-action").performClick()
        compose.runOnIdle { assertEquals(1,imports) }
    }
    @Test fun pilotSpeedHandleDragsContinuouslyInBothModesForShipsAndRockets() {
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val prior=prefs.getBoolean("motionControl",false)
        val priorMode=prefs.getString("flightControl",null)
        // This scenario exercises the horizontal tilt-mode slider, regardless of test order.
        prefs.edit().putBoolean("motionControl",true).putString("flightControl","Tilt").commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState()
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            for (mode in AppMode.entries) for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
                compose.runOnIdle {
                    if (mode == AppMode.Sandbox) { game.startSandbox(SandboxPresetKind.Empty); game.toggleSandboxPause() }
                    else game.startArcade()
                    game.chooseSpawnKind(kind)
                }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag(if (mode == AppMode.Sandbox) "sandbox-motion-control" else "arcade-motion-control").performClick()
                compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(0f,-100f)) }
                compose.mainClock.advanceTimeByFrame()
                val id=game.controlledVehicleId!!
                val count=game.bodies.size; val camera=game.camera
                compose.onNodeWithTag("flight-joystick").assertDoesNotExist()
                compose.onNodeWithTag("pilot-speed").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(180f)) }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("pilot-speed").performTouchInput { down(Offset(width*.2f,center.y)) }
                compose.mainClock.advanceTimeByFrame()
                var previous=180.0
                for (fraction in listOf(.4f,.65f,.9f,.6f,.2f)) {
                    compose.onNodeWithTag("pilot-speed").performTouchInput { moveTo(Offset(width*fraction,center.y),100) }
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle {
                        val target=game.bodies.first { it.id == id }.pilotTargetSpeed!!
                        assertTrue("Speed must follow the finger before it lifts: $mode $kind fraction=$fraction $previous -> $target",kotlin.math.abs(target-previous) > 100)
                        assertEquals(count,game.bodies.size)
                        assertEquals(camera.zoom,game.camera.zoom)
                        if (mode == AppMode.Sandbox) assertEquals(camera,game.camera)
                        else {
                            val point=worldToScreen(game.bodies.first { it.id == id }.position,game.viewport,
                                game.camera.center,game.camera.zoom,game.cameraRotation)
                            val anchor=Offset(game.viewport.width/2f,game.viewport.height/2f)+pilotAnchorOffset(game.viewport).toOffset()
                            assertTrue("Camera must keep the craft within its manoeuvre leash",(point-anchor).getDistance() <= minOf(game.viewport.width,game.viewport.height)*.18f+.01f)
                        }
                        previous=target
                    }
                }
                compose.onNodeWithTag("pilot-speed").performTouchInput { up() }
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("pilot-fuel").assertIsDisplayed()
                shot("pilot-speed-handle.png")
            }
        } finally { prefs.edit().putBoolean("motionControl",prior).apply {
            if (priorMode == null) remove("flightControl") else putString("flightControl",priorMode)
        }.commit() }
    }
    @Test fun stellarCollisionAndAStalledFrameKeepTheSandboxVisible() {
        compose.mainClock.autoAdvance=false
        val stars=listOf(CelestialBody(101,Vec2(-11.0,0.0),Vec2(80.0,0.0),100.0,10f,Color.Yellow,BodyKind.Star),
            CelestialBody(102,Vec2(11.0,0.0),Vec2(-80.0,0.0),100.0,10f,Color.Cyan,BodyKind.Star))
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(stars,Vec2.Zero,3f,0.0,0,
            collisionsEnabled=true,collisionMode=SandboxCollisionMode.Debris)) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.runOnIdle { repeat(20) { game.update(1.0/60) } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(game.menuOpen); assertEquals(1,game.bodies.count { it.kind == BodyKind.Star }) }
        compose.onNodeWithTag("sandbox-pause").assertIsDisplayed()
        shot("stellar-debris-impact.png")
        compose.runOnIdle { game.update(3.0); assertFalse(game.menuOpen); assertTrue(game.sandbox!!.paused) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("sandbox-pause").assertIsDisplayed()
        compose.onNodeWithTag("menu-primary").assertDoesNotExist()
    }
}
