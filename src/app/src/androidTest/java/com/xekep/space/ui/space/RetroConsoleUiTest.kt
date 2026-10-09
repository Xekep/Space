package com.xekep.space.ui.space

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class RetroConsoleUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get()=context.getSharedPreferences("space_options",Context.MODE_PRIVATE)
    private var existed=false
    private var previous=false
    @Before fun resetOnlyRetroOption() {
        existed=preferences.contains("retroConsole"); previous=preferences.getBoolean("retroConsole",false)
        preferences.edit().remove("retroConsole").commit()
    }
    @After fun restoreOnlyRetroOption() {
        val edit=preferences.edit()
        if (existed) edit.putBoolean("retroConsole",previous) else edit.remove("retroConsole")
        edit.commit()
    }
    private fun shot(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun framebufferProducesSharpPixelsClearsPreviousFrameAndKeepsWorldCoordinates() {
        val renderer=RetroRenderer()
        val image=ImageBitmap(96,96)
        val canvas=Canvas(image)
        val scope=CanvasDrawScope()
        scope.draw(Density(1f),LayoutDirection.Ltr,canvas,Size(96f,96f)) {
            drawRetroFrame(renderer) {
                assertEquals(Size(96f,96f),size)
                drawRect(Color.Black)
                drawCircle(Color.White,8.3f,Offset(46f,46f))
            }
        }
        val bitmap=image.asAndroidBitmap()
        var bright=0
        for (y in 0 until 96 step 3) for (x in 0 until 96 step 3) {
            val color=bitmap.getPixel(x,y)
            if (android.graphics.Color.red(color) > 200) bright++
            for (dy in 0..2) for (dx in 0..2) assertEquals("Pixel must have hard edges",color,bitmap.getPixel(x+dx,y+dy))
        }
        assertTrue(bright in 10..40)
        assertEquals(IntSize(32,32),renderer.bufferSize)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        scope.draw(Density(1f),LayoutDirection.Ltr,canvas,Size(96f,96f)) { drawRetroFrame(renderer) {} }
        assertEquals(android.graphics.Color.TRANSPARENT,bitmap.getPixel(46,46))
        scope.draw(Density(2f),LayoutDirection.Ltr,canvas,Size(96f,96f)) { drawRetroFrame(renderer) { drawRect(Color.Black) } }
        assertEquals(IntSize(16,16),renderer.bufferSize)
    }

    @Test fun switchPersistsAcrossModesAndCanRestoreTheNormalPicture() {
        compose.mainClock.autoAdvance=false
        val bodies=listOf(
            CelestialBody(800001,Vec2.Zero,Vec2.Zero,6000.0,44f,Color(0xFFFFD898),BodyKind.Star),
            CelestialBody(800002,Vec2(-170.0,-130.0),Vec2.Zero,200.0,28f,Color(0xFF77AAFF),BodyKind.Ambient),
            CelestialBody(800003,Vec2(145.0,180.0),Vec2.Zero,70.0,16f,Color.Cyan,BodyKind.Ship),
            CelestialBody(800004,Vec2(-145.0,185.0),Vec2.Zero,30.0,14f,Color.White,BodyKind.Rocket))
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(bodies,Vec2.Zero,1.5f,0.0,0,paused=true,preset=SandboxPresetKind.Empty)) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertDoesNotExist()
        shot("retro-console-before.png")
        compose.runOnIdle { game.openMenu() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("retro-console-switch").performScrollTo().assertIsOff().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-console-switch").assertIsOn()
        assertTrue(GameOptions(context).retroConsole)
        compose.onNodeWithTag("close-menu-panel").performClick()
        compose.mainClock.advanceTimeBy(350)
        shot("retro-console-menu.png")
        compose.runOnIdle { game.closeMenu() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertExists()
        shot("retro-console-after.png")
        val before=game.bodies.toList()
        val zoom=game.camera.zoom
        compose.onNodeWithTag("space-scene").performTouchInput {
            down(0,center+Offset(-90f,50f)); down(1,center+Offset(90f,50f))
            moveTo(0,center+Offset(-170f,50f)); moveTo(1,center+Offset(170f,50f)); up(0); up(1)
        }
        compose.runOnIdle { assertTrue(game.camera.zoom > zoom); assertEquals(before,game.bodies); game.startArcade() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("arcade-spawn-Ship").performClick()
        compose.onNodeWithTag("space-scene").performTouchInput { click(center+Offset(0f,-240f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(game.bodies.any { it.kind == BodyKind.Ship }); game.openMenu() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("retro-console-switch").performScrollTo().assertIsOn().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertFalse(GameOptions(context).retroConsole)
        compose.onNodeWithTag("retro-screen-overlay").assertDoesNotExist()
    }

    @Test fun thousandBodySceneDrawsAndMenuIsStillReachableWithFilterEnabled() {
        GameOptions(context).apply { retroConsole=true; save() }
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.SystemGalaxy); toggleSandboxPause() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertExists()
        compose.runOnIdle { assertEquals(1000,game.bodies.size) }
        shot("retro-console-galaxy.png")
        compose.onNodeWithTag("open-menu").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertTrue(game.menuOpen) }
        compose.onNodeWithTag("open-settings").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("retro-console-switch").performScrollTo().assertIsOn().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("retro-screen-overlay").assertDoesNotExist()
    }
}
