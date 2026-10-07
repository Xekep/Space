package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.BodyKind
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompactArcadeHudUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun compactBarKeepsBothIconActionsAndStatsDoNotSpawnObjects() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bar=compose.onNodeWithTag("arcade-top-hud")
        assertTrue(bar.fetchSemanticsNode().boundsInRoot.height/compose.density.density <= 64f)
        compose.onNodeWithText(context.getString(com.xekep.space.R.string.menu)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(com.xekep.space.R.string.find_core)).assertDoesNotExist()
        val count=game.bodies.size
        bar.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(count,game.bodies.size) }
        compose.runOnIdle { game.transformCamera(Offset(500f,700f),Offset(3000f,0f),1f) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertIsDisplayed()
        compose.onNodeWithTag("find-core").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertDoesNotExist()
        compose.runOnIdle { assertEquals(game.bodies.first { it.kind == BodyKind.Core }.position,game.camera.center) }
        File(context.externalCacheDir,"compact-arcade-hud.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.onNodeWithTag("open-menu").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("menu-primary").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("find-core").assertIsDisplayed()
    }
}
