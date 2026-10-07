package com.xekep.space.ui.space

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MenuCosmosUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun backgroundMovesWhileTheGameWorldStaysFrozenAndDisappearsOnResume() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars); openMenu() }
        val bodies=game.bodies
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        val first=compose.onNodeWithTag("menu-cosmos").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(4000)
        val second=compose.onNodeWithTag("menu-cosmos").captureToImage().asAndroidBitmap()
        var changed=0
        for (y in 0 until first.height step 3) for (x in 0 until first.width step 3)
            if (first.getPixel(x,y) != second.getPixel(x,y)) changed++
        assertTrue("The star system must move",changed > 100)
        compose.runOnIdle { assertSame(bodies,game.bodies); assertTrue(game.menuOpen) }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,"menu-live-cosmos.png").outputStream().use {
            assertTrue(second.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.onNodeWithTag("menu-primary").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("menu-cosmos").assertDoesNotExist()
    }
}
