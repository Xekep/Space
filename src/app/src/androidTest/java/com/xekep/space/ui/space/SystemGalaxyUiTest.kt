package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
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
import kotlin.random.Random

class SystemGalaxyUiTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        android.os.SystemClock.sleep(250); instrumentation.waitForIdleSync()
        File(instrumentation.targetContext.externalCacheDir,name).outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun menuPresetsGenerateHierarchyAndToolsOmitGenerationButtons() {
        val game=SpaceGameState(random=Random(17)).apply { startSandbox(SandboxPresetKind.BinaryStars); toggleSandboxPause(); openMenu() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("preset-RandomSystems").assertIsDisplayed()
        compose.onNodeWithTag("preset-SystemGalaxy").performClick()
        screenshot("two-galaxies-menu.png")
        compose.onNodeWithTag("new-session").performClick()
        compose.onNodeWithTag("confirm-action").performClick()
        compose.runOnIdle { game.toggleSandboxPause(); assertEquals(1000,game.bodies.size); assertEquals(SandboxPresetKind.SystemGalaxy,game.sandbox!!.preset) }
        screenshot("system-galaxy-1000.png")
        compose.runOnIdle {
            val storage=SandboxStorage(instrumentation.targetContext)
            val snapshot=game.snapshot(123)!!
            val decoded=storage.decode(storage.encode(snapshot))
            assertEquals(SandboxPresetKind.SystemGalaxy,decoded.preset)
            assertEquals(snapshot.bodies.map { it.galaxySystemId to it.orbitParentId },decoded.bodies.map { it.galaxySystemId to it.orbitParentId })
            assertEquals(333,decoded.bodies.count { it.labelId() == com.xekep.space.R.string.galaxy_planet })
            assertEquals(555,decoded.bodies.count { it.labelId() == com.xekep.space.R.string.galaxy_moon })
            game.startSandbox(SandboxPresetKind.BinaryStars); game.toggleSandboxPause()
        }
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.onNodeWithTag("generate-random-systems").assertDoesNotExist()
        compose.onNodeWithTag("generate-system-galaxy").assertDoesNotExist()
        // The reversible generation API still supports older callers; no duplicate tools UI.
        compose.runOnIdle { game.generateRandomSystems("fixture",resolved=true) }
        compose.onNodeWithTag("close-sandbox-panel").performClick()
        compose.runOnIdle { assertEquals(1000,game.bodies.size); assertTrue(game.sandbox!!.paused) }
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.onNodeWithTag("sandbox-undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2,game.bodies.size) }
    }

    @Test fun thousandBodiesAdvanceInBackgroundWithPinchAndMenuStillUsable() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState(random=Random(73)).apply { startSandbox(SandboxPresetKind.SystemGalaxy) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val initial=game.bodies
        compose.mainClock.advanceTimeBy(64)
        compose.waitUntil(15000) { game.bodies !== initial }
        val before=game.camera.zoom
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(0,center-Offset(90f,0f)); down(1,center+Offset(90f,0f))
            moveTo(0,center-Offset(160f,0f)); moveTo(1,center+Offset(160f,0f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(1000,game.bodies.size); assertTrue(game.camera.zoom > before) }
        compose.onNodeWithTag("open-menu").performClick()
        compose.runOnIdle { assertTrue(game.menuOpen) }
        var sample=game.bodies
        val timings=List(30) {
            val start=System.nanoTime()
            sample=SimulationEngine.stepSandbox(sample,1.0/30,0.0,true,collisionMode=SandboxCollisionMode.Debris).bodies
            (System.nanoTime()-start)/1e6
        }.sorted()
        println("SYSTEM_GALAXY_ANDROID_STEP,p50Ms=${timings[15]},p95Ms=${timings[28]},bodies=${sample.size}")
        // Inspect an actual resolved family at the scale where planets and moons are visible.
        compose.runOnIdle {
            game.closeMenu(); game.toggleSandboxPause()
            val star=game.bodies.first { it.kind == BodyKind.Star }
            game.selectBody(star.id); game.followSelected()
            game.transformCamera(Offset(game.viewport.width/2f,game.viewport.height/2f),Offset.Zero,.4f/game.camera.zoom)
            assertEquals(.4f,game.camera.zoom,.001f)
        }
        screenshot("system-galaxy-family.png")
    }
}
