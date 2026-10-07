package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CoreDirectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pilotingKeepsTheCoreArrowVisibleAboveTheBottomHudAndAfterTurning() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("space_options",android.content.Context.MODE_PRIVATE)
        val previous=prefs.getBoolean("motionControl",false)
        prefs.edit().putBoolean("motionControl",true).commit()
        compose.mainClock.autoAdvance=false
        try {
            val game=SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.runOnIdle {
                val point=game.bodies.first { it.kind == com.xekep.space.sim.BodyKind.Core }.position+
                    com.xekep.space.sim.Vec2(0.0,-4000.0)
                game.chooseSpawnKind(com.xekep.space.sim.BodyKind.Ship)
                game.launch(TouchPreview(point,point,0),0.0)
                game.fitCamera()
                assertNotNull(game.controlledVehicleId)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("core-direction").assertDoesNotExist()
            val baseline=compose.onRoot().captureToImage().asAndroidBitmap()
            compose.runOnIdle { game.transformCamera(Offset.Zero,Offset.Zero,1f) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("core-direction").assertIsDisplayed()
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            File(context.externalCacheDir,"core-direction-pilot-bottom.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
            val padding=(48*compose.density.density).toInt()
            fun yellowPixels(image: android.graphics.Bitmap): Int {
                var count=0
                for (x in image.width/2-padding..image.width/2+padding) for (y in image.height-padding*2 until image.height) {
                    val color=image.getPixel(x,y)
                    if (android.graphics.Color.red(color)>240 && android.graphics.Color.green(color) in 195..220 &&
                        android.graphics.Color.blue(color) in 85..115) count++
                }
                return count
            }
            val yellow=yellowPixels(bitmap)-yellowPixels(baseline)
            assertTrue("The core arrow must remain visible above the pilot HUD (yellow pixels: $yellow)",yellow>20)
            compose.runOnIdle {
                game.setSteeringInput(com.xekep.space.sim.Vec2(1.0,0.0))
                repeat(20) { game.update(1.0/60) }
                game.setSteeringInput(com.xekep.space.sim.Vec2.Zero)
                repeat(90) { game.update(1.0/60) }
                assertTrue(kotlin.math.abs(game.cameraRotation)>1)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("core-direction").assertIsDisplayed()
            File(context.externalCacheDir,"core-direction-pilot-rotated.png").outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
        } finally { prefs.edit().putBoolean("motionControl",previous).commit() }
    }

    @Test fun panningAwayShowsAYellowArrowAndReturningOrOpeningMenuHidesIt() {
        compose.mainClock.autoAdvance = false
        val game = SpaceGameState().apply { resize(IntSize(1080,2340)); startArcade() }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.onNodeWithTag("core-direction").assertDoesNotExist()
        repeat(3) {
            compose.onNodeWithTag("space-scene").performTouchInput {
                val a = Offset(width*.15f,height*.4f); val b = Offset(width*.4f,height*.4f)
                down(0,a); down(1,b)
                updatePointerTo(0,a+Offset(width*.45f,0f)); updatePointerTo(1,b+Offset(width*.45f,0f))
                move(); up(1); up(0)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val padding = (48*compose.density.density).toInt()
        var yellow = 0
        for (x in bitmap.width-padding until bitmap.width) for (y in bitmap.height/2-padding..bitmap.height/2+padding) {
            val color = bitmap.getPixel(x,y)
            if (android.graphics.Color.red(color)>240 && android.graphics.Color.green(color) in 195..220 &&
                android.graphics.Color.blue(color) in 85..115) yellow++
        }
        assertTrue("A yellow core arrow must be drawn at the right edge",yellow>20)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,"core-direction.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        compose.onNodeWithTag("open-menu").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertDoesNotExist()
        compose.onNodeWithTag("menu-primary").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertIsDisplayed()
        compose.runOnIdle { game.fitCamera() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertDoesNotExist()
        compose.runOnIdle { game.startSandbox(com.xekep.space.sim.SandboxPresetKind.Empty) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("core-direction").assertDoesNotExist()
    }
}
