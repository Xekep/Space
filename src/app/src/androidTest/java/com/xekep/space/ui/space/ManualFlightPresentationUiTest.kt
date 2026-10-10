package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ManualFlightPresentationUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun ordinaryAndPilotWakesCompareCentreWithSternUnderTheSameCamera() {
        compose.mainClock.autoAdvance=false
        val kind=mutableStateOf(BodyKind.Ship)
        val heavy=mutableStateOf(false)
        compose.setContent {
            SpaceTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF060D1D))) {
                    Text("Normal: centre / stern",Modifier.padding(8.dp),color=Color.White)
                    Canvas(Modifier.fillMaxWidth().weight(1f)) {
                        drawPair(kind.value,heavy.value,false)
                    }
                    Text("Pilot: centre / stern, pitch + bank",Modifier.padding(8.dp),color=Color.White)
                    Canvas(Modifier.fillMaxWidth().weight(1f)) {
                        drawPair(kind.value,heavy.value,true)
                    }
                }
            }
        }
        for (type in listOf(BodyKind.Ship,BodyKind.Rocket)) for (armored in listOf(false,true)) {
            compose.runOnIdle { kind.value=type; heavy.value=armored }
            compose.mainClock.advanceTimeBy(48)
            screenshot("wake-compare-${type.name}-${if (armored) "heavy" else "standard"}.png")
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPair(kind: BodyKind,heavy: Boolean,pilot: Boolean) {
        val viewport=IntSize(size.width.toInt(),size.height.toInt())
        val heading=Vec2(.25,-.9682458365518543)
        val body=CelestialBody(1,Vec2.Zero,heading*240.0,
            if (heavy) heavyHullMass(kind) else if (kind == BodyKind.Ship) 24.0 else 12.0,
            12f*density,Color(0xFF80FFDF),kind,heading=heading,pilotTargetSpeed=240.0,pilotThrottle=.55,
            pitch=if (pilot) .65 else 0.0,roll=if (pilot) .9 else 0.0,
            trail=(30 downTo 0).map { Vec2(-it*5.0,it*10.0) })
        val radius=vehicleRenderRadius(body,1f,density,true,if (pilot) 1.4f else null)
        for (correct in listOf(false,true)) {
            val centre=Vec2(if (correct) -size.width*.25 else size.width*.25,0.0)
            rotate(if (pilot) -20f else 0f) {
                drawTrail(body,viewport,centre,1f,highlighted=true,
                    vehicleRadius=if (correct) radius else null,renderHeading=heading)
                drawBody(body,viewport,centre,1f,piloted=pilot,largeVehicleIcons=true,
                    renderHeading=heading,pilotVisualZoom=if (pilot) 1.4f else null)
            }
        }
    }

    @Test fun releasingTheRealStickLevelsCraftWhileTheCameraFollowsWithLag() {
        val prefs=context.getSharedPreferences("space_options",0)
        val old=prefs.getString("flightControl",null)
        prefs.edit().putString("flightControl","Joystick").commit()
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        try {
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.mainClock.advanceTimeBy(48)
            for (scene in listOf("Arcade","Empty","SolarSystem"))
            for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1f,1f)) {
                compose.runOnIdle {
                    if (scene == "Arcade") game.startArcade() else game.startSandbox(SandboxPresetKind.valueOf(scene))
                    if (scene == "SolarSystem") game.setTimeScale(.0001)
                    game.setMotionControlEnabled(true); game.chooseSpawnKind(kind)
                    val point=if (scene == "Empty") Vec2.Zero else Vec2(10000.0,10000.0)
                    game.launch(TouchPreview(point,point+Vec2(0.0,-100.0),0),0.0)
                    game.setPilotTargetSpeed(240.0)
                }
                compose.mainClock.advanceTimeBy(48)
                val id=game.controlledVehicleId!!
                compose.onNodeWithTag("pitch-joystick").performTouchInput {
                    down(center); moveTo(center+Offset(axis*28*game.density,axis*28*game.density),16)
                }
                compose.runOnIdle { repeat(12) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                val held=game.bodies.first { it.id == id }
                compose.runOnIdle {
                    assertTrue(held.pitch*axis > .3); assertTrue(held.roll*axis > .45)
                    val target=pilotCameraTarget(held)
                    val error=kotlin.math.atan2(kotlin.math.sin(target-game.cameraRotation),kotlin.math.cos(target-game.cameraRotation))
                    assertTrue("Camera caught the manoeuvre immediately: $scene $kind",kotlin.math.abs(error) > .015)
                }
                screenshot("level-flight-$scene-${kind.name}-${if (axis > 0) "right" else "left"}-held.png")
                compose.onNodeWithTag("pitch-joystick").performTouchInput { up() }
                compose.runOnIdle { repeat(3) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val returning=game.bodies.first { it.id == id }
                    assertTrue(kotlin.math.abs(returning.pitch) in .05..<kotlin.math.abs(held.pitch))
                    assertTrue(kotlin.math.abs(returning.roll) in .05..<kotlin.math.abs(held.roll))
                    assertEquals(240.0,returning.pilotTargetSpeed!!,0.0)
                }
                compose.runOnIdle { repeat(180) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val level=game.bodies.first { it.id == id }
                    assertEquals(0.0,level.pitch,.001); assertEquals(0.0,level.roll,.001)
                    val onScreen=rotateVector(level.heading,game.cameraRotation)
                    assertEquals(0.0,onScreen.x,.015)
                    val point=worldToScreen(level.position,game.viewport,game.camera.center,game.camera.zoom,game.cameraRotation)
                    assertTrue(kotlin.math.hypot((point.x-game.viewport.width/2f).toDouble(),(point.y-game.viewport.height/2f).toDouble()) <= minOf(game.viewport.width,game.viewport.height)*.18+.01)
                }
                screenshot("level-flight-$scene-${kind.name}-${if (axis > 0) "right" else "left"}-released.png")
            }
        } finally {
            prefs.edit().apply { if (old == null) remove("flightControl") else putString("flightControl",old) }.commit()
        }
    }

    @Test fun realRightStickShowsPitchPerspectiveWithin100msAndBanksIntoATurnForBothCraft() {
        val prefs=context.getSharedPreferences("space_options",0)
        val old=prefs.getString("flightControl",null)
        prefs.edit().putString("flightControl","Joystick").commit()
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        try {
            compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
            compose.mainClock.advanceTimeBy(48)
            for (preset in listOf(SandboxPresetKind.Empty,SandboxPresetKind.SolarSystem))
            for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (axis in listOf(-1f,1f)) {
                compose.runOnIdle {
                    game.startSandbox(preset)
                    if (preset == SandboxPresetKind.SolarSystem) game.setTimeScale(.0001)
                    game.setMotionControlEnabled(true); game.chooseSpawnKind(kind)
                    val point=if (preset == SandboxPresetKind.SolarSystem) Vec2(10000.0,0.0) else Vec2.Zero
                    game.launch(TouchPreview(point,point+Vec2(0.0,-100.0),0),0.0)
                    game.setPilotTargetSpeed(240.0)
                }
                compose.mainClock.advanceTimeBy(48)
                val id=game.controlledVehicleId!!
                val before=game.bodies.first { it.id == id }.flightVisualScale()
                compose.onNodeWithTag("pitch-joystick").performTouchInput {
                    down(center); moveTo(center+Offset(0f,axis*32*game.density),16)
                }
                // Fixed 100 ms of input time through the actual world controller. Solar's
                // asynchronous physics publication is not driven by Compose test-clock frames.
                compose.runOnIdle { repeat(6) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val craft=game.bodies.first { it.id == id }
                    assertEquals(id,craft.id); assertTrue(craft.verticalVelocity*axis > 0)
                    assertTrue("$preset $kind axis=$axis pitch=${craft.pitch} z=${craft.flightHeight} scale=${craft.flightVisualScale()}",
                        craft.pitch*axis > .30)
                    val nose=vehiclePitchMatrix(craft.pitch,20f,craft.roll).map(Offset(10f,-20f))
                    assertTrue(if (axis > 0) nose.x > 10f else nose.x < 10f)
                    assertTrue(craft.flightHeight*axis > .5)
                    assertTrue(if (axis > 0) craft.flightVisualScale() > before*1.07f else craft.flightVisualScale() < before*.93f)
                }
                compose.onNodeWithTag("pitch-joystick").performTouchInput {
                    moveTo(center+Offset(axis*32*game.density,0f),16)
                }
                val heading=game.bodies.first { it.id == id }.heading
                compose.runOnIdle { repeat(12) { game.update(1.0/60) } }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val craft=game.bodies.first { it.id == id }
                    assertTrue(craft.roll*axis > .5)
                    val cross=heading.x*craft.heading.y-heading.y*craft.heading.x
                    assertTrue("Bank must turn in real time: $preset $kind axis=$axis cross=$cross",cross*axis > .04)
                    assertTrue(kotlin.math.abs(game.cameraRotation) > .015)
                }
                screenshot("fast-manoeuvre-${preset.name}-${kind.name}-${if (axis > 0) "up-right" else "down-left"}.png")
                compose.onNodeWithTag("pitch-joystick").performTouchInput { up() }
                compose.mainClock.advanceTimeByFrame()
            }
        } finally {
            prefs.edit().apply { if (old == null) remove("flightControl") else putString("flightControl",old) }.commit()
        }
    }
}
