package com.xekep.space.ui.space

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GalaxyUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun variedGalaxiesStayReadableAndMenuPresetsAndPinchWork() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        compose.mainClock.autoAdvance=false
        fun scene(seed: Int)=SpaceGameState(random=Random(seed)).apply {
            startSandbox(SandboxPresetKind.Empty); toggleSandboxPause()
        }
        val shown=mutableStateOf(scene(7))
        compose.setContent { SpaceTheme { SpaceSceneRoot(shown.value) } }
        for (seed in listOf(7,17,53)) {
            if (seed != 7) { compose.runOnIdle { shown.value=scene(seed) }; compose.mainClock.advanceTimeBy(48) }
            val game=shown.value
            compose.onNodeWithTag("open-menu").performClick()
            compose.mainClock.advanceTimeByFrame()
            val started=System.nanoTime()
            compose.onNodeWithTag("preset-RandomSystems").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("new-session").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("confirm-action").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { game.toggleSandboxPause() }
            val generateMs=(System.nanoTime()-started)/1e6
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(48)
            assertEquals(500,game.bodies.size)
            assertTrue(game.bodies.count { it.kind == BodyKind.Star } > 400)
            val hole=game.bodies.first { it.kind == BodyKind.BlackHole }
            val stars=game.bodies.filter { it.kind == BodyKind.Star }
            val smallest=stars.minOf { bodyScreenRadius(it,game.camera.zoom,game.density) }
            assertTrue(bodyScreenRadius(hole,game.camera.zoom,game.density)/smallest < 5)
            compose.onNodeWithTag("object-counter").assertExists()
            compose.waitForIdle(); android.os.SystemClock.sleep(250); instrumentation.waitForIdleSync()
            File(context.externalCacheDir,"galaxy-seed-$seed.png").outputStream().use {
                assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            val before=game.camera.zoom
            compose.onNodeWithTag("space-scene").performTouchInput {
                down(0,center-Offset(90f,0f)); down(1,center+Offset(90f,0f))
                moveTo(0,center-Offset(160f,0f)); moveTo(1,center+Offset(160f,0f)); up(0); up(1)
            }
            assertTrue(game.camera.zoom > before)
            assertEquals(500,game.bodies.size)
            android.util.Log.i("SpaceGalaxy","seed=$seed bodies=${game.bodies.size} generationAndUiMs=$generateMs zoom=${game.camera.zoom}")
        }
    }
}
