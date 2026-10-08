package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SandboxPhysicsUiTest {
    @get:Rule val compose=createComposeRule()
    private fun shot(name: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun accretedStarCollapsesInPlaceAndItsAnimationEndsWithoutLeavingTheGame() {
        compose.mainClock.autoAdvance=false
        val bodies=listOf(CelestialBody(60001,Vec2(-18.0,0.0),Vec2(3.0,2.0),15000.0,20f,Color.Yellow,BodyKind.Star),
            CelestialBody(60002,Vec2(18.0,0.0),Vec2(-1.0,-2.0),4000.0,20f,Color.Cyan))
        val game=SpaceGameState().apply {
            loadSandbox(SandboxSnapshot(bodies,Vec2.Zero,3f,0.0,0,collisionsEnabled=true,paused=true,collisionMode=SandboxCollisionMode.Debris))
        }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            game.toggleSandboxPause(); game.update(.05); game.toggleSandboxPause()
            assertEquals(BodyKind.BlackHole,game.bodies.single().kind)
            assertEquals(19000.0,game.bodies.single().mass,1e-8)
            assertTrue(game.explosions.any { it.collapseRadius > 0 }); assertFalse(game.menuOpen)
        }
        compose.mainClock.advanceTimeByFrame(); shot("black-hole-collapse-start.png")
        compose.runOnIdle {
            game.toggleSandboxPause(); repeat(60) { game.update(1.0/60) }; game.toggleSandboxPause()
        }
        compose.mainClock.advanceTimeByFrame(); shot("black-hole-collapse-mid.png")
        compose.runOnIdle {
            game.toggleSandboxPause(); repeat(120) { game.update(1.0/60) }; game.toggleSandboxPause()
            assertTrue(game.explosions.isEmpty()); assertFalse(game.menuOpen)
        }
        compose.mainClock.advanceTimeByFrame(); shot("black-hole-final.png")
    }
    @Test fun zeroThrustImmediatelyDisablesBothShipEnginesAndManualTurning() {
        compose.mainClock.autoAdvance=false
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); setMotionControlEnabled(true) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            compose.runOnIdle {
                game.setMotionControlEnabled(true)
                game.chooseSpawnKind(kind)
                val point=Vec2(if (kind == BodyKind.Ship) -60.0 else 60.0,0.0)
                game.launch(TouchPreview(point,point+Vec2(0.0,-90.0),0),0.0)
                game.setPilotTargetSpeed(0.0)
                val body=game.bodies.last()
                assertFalse(body.enginePowered)
                game.setSteeringInput(Vec2(1.0,0.0)); game.update(.1)
                val drifting=game.bodies.last()
                assertEquals(body.heading,drifting.heading)
                assertEquals(body.fuelRemaining,drifting.fuelRemaining,0.0)
                assertEquals(0.0,drifting.pilotThrottle,0.0)
            }
            compose.mainClock.advanceTimeByFrame()
            shot(if (kind == BodyKind.Ship) "ship-zero-thrust.png" else "rocket-zero-thrust.png")
        }
    }
    @Test fun satelliteAndSubSatelliteAreSmallerAndLaunchThroughTheActualGesture() {
        compose.mainClock.autoAdvance=false
        val parent=CelestialBody(60010,Vec2.Zero,Vec2(200.0,-100.0),1.0,2f,Color.Yellow,physicalScale=true)
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(listOf(parent),Vec2.Zero,5f,0.0,0,paused=true)) }
        compose.setContent { SpaceTheme { SpaceSceneRoot(game) } }
        compose.mainClock.advanceTimeByFrame()
        var center=parent
        repeat(2) {
            compose.runOnIdle { game.selectBody(center.id) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("orbit-helper").performClick()
            compose.mainClock.advanceTimeByFrame()
            val radius=center.radius*(if (it == 0) 16.0 else 4.0)
            compose.runOnIdle {
                game.transformCamera(androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset.Zero,
                    minOf(1f,game.viewport.width*.25f/radius.toFloat()/game.camera.zoom))
            }
            compose.mainClock.advanceTimeByFrame()
            val point=worldToScreen(center.position+Vec2(radius,0.0),game.viewport,game.camera.center,game.camera.zoom)
            compose.onNodeWithTag("space-scene").performTouchInput { click(point) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(it+2,game.bodies.size)
                val satellite=game.bodies.last()
                assertTrue(satellite.mass < center.mass); assertTrue(satellite.radius < center.radius)
                assertEquals(SimulationEngine.orbitVelocity(center,satellite.position,satellite.mass),satellite.velocity)
                center=satellite
            }
        }
        shot("nested-satellites.png")
    }
}
