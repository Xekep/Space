package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxStorage
import com.xekep.space.storage.SandboxSnapshot
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RandomSystemsUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        File(context.externalCacheDir,name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun menuGeneratesFiveHundredBodiesAndToolsRetainCollisionOptions() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("sandbox-collisions").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("collision-mode-Debris").performScrollTo().performClick()
        compose.onNodeWithTag("generate-random-systems").assertDoesNotExist()
        compose.onNodeWithTag("generate-system-galaxy").assertDoesNotExist()
        compose.onNodeWithTag("close-sandbox-panel").performClick()
        compose.onNodeWithTag("open-menu").performClick()
        compose.onNodeWithTag("preset-RandomSystems").performClick()
        compose.onNodeWithTag("new-session").performClick()
        compose.onNodeWithTag("confirm-action").performClick()
        compose.runOnIdle { game.toggleSandboxPause() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(500,game.bodies.size); assertTrue(game.bodies.count { it.kind == BodyKind.Star } > 400); assertEquals(1,game.bodies.count { it.kind == BodyKind.BlackHole })
            assertEquals(SandboxCollisionMode.Debris,game.sandbox!!.collisionMode); assertTrue(game.sandbox!!.paused)
            val storage=SandboxStorage(context); val decoded=storage.decode(storage.encode(game.snapshot(123)!!))
            assertEquals(500,decoded.bodies.size); assertEquals(SandboxCollisionMode.Debris,decoded.collisionMode)
            val legacy=org.json.JSONObject(storage.encode(decoded)).apply { remove("collisionMode") }.toString()
            assertEquals(SandboxCollisionMode.Merge,storage.decode(legacy).collisionMode)
        }
        screenshot("random-systems-500.png")

    }
    @Test fun debrisModeCreatesPhysicalFragmentsThroughTheToolsPanel() {
        val pair=listOf(CelestialBody(20001,Vec2(-11.0,0.0),Vec2(80.0,0.0),100.0,10f,Color.Cyan),
            CelestialBody(20002,Vec2(11.0,0.0),Vec2(-80.0,0.0),100.0,10f,Color.Yellow))
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(pair,Vec2.Zero,1f,0.0,0,paused=true)) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("sandbox-tools").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("sandbox-collisions").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("collision-mode-Debris").performScrollTo().performClick()
        compose.onNodeWithTag("close-sandbox-panel").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            game.transformCamera(Offset(game.viewport.width/2f,game.viewport.height/2f),Offset.Zero,8f)
            game.toggleSandboxPause(); game.update(.02); game.toggleSandboxPause()
            assertTrue(game.bodies.size > 2); assertEquals(200.0,game.bodies.sumOf { it.mass },1e-8)
            assertTrue(game.explosions.isNotEmpty())
        }
        compose.mainClock.advanceTimeByFrame()
        screenshot("debris-collision.png")
    }
    @Test fun fiveHundredBodiesAdvanceInBackgroundWhileCameraAndMenuRemainUsable() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(); generateRandomSystems("500") }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val initial=game.bodies
        compose.mainClock.advanceTimeBy(64)
        compose.waitUntil(15000) { game.bodies !== initial }
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(0,center-Offset(100f,0f)); down(1,center+Offset(100f,0f))
            moveTo(0,center-Offset(130f,0f)); moveTo(1,center+Offset(130f,0f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(500,game.bodies.size); assertFalse(game.menuOpen) }
        compose.onNodeWithTag("open-menu").performClick()
        compose.runOnIdle { assertTrue(game.menuOpen) }
    }
}
