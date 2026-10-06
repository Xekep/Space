package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class CelestialCreationTest {
    @Test fun sandboxCyclesThroughFiveKindsAndArcadeKeepsItsThreeWeapons() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket,BodyKind.Star,BodyKind.BlackHole,BodyKind.Ambient)) {
            game.cycleSpawnKind(); assertEquals(kind,game.spawnKind)
        }
        game.chooseSpawnKind(BodyKind.BlackHole); game.enterMode(AppMode.Arcade)
        assertEquals(BodyKind.Ambient,game.spawnKind)
        game.chooseSpawnKind(BodyKind.Star); assertEquals(BodyKind.Ambient,game.spawnKind)
    }
    @Test fun starsAndHolesLaunchAtTheTouchPointWithAdjustableMassAndCompactHorizon() {
        val game=SpaceGameState().apply { resize(IntSize(1080,1920)); startSandbox(SandboxPresetKind.Empty) }
        val point=Vec2(9000.0,-8000.0)
        for (kind in listOf(BodyKind.Star,BodyKind.BlackHole)) {
            game.chooseSpawnKind(kind)
            val short=game.previewBody(TouchPreview(point,point,0),0.0)!!
            val long=game.previewBody(TouchPreview(point,point,0),3.0)!!
            assertTrue(long.mass > short.mass); assertTrue(long.radius > short.radius)
            game.launch(TouchPreview(point,point,0),1.0)
            assertEquals(kind,game.bodies.last().kind); assertEquals(point,game.bodies.last().position)
            assertEquals(short.color,game.bodies.last().color)
            game.undo()
        }
        game.chooseSpawnKind(BodyKind.BlackHole)
        assertTrue(game.previewBody(TouchPreview(point,point,0),0.0)!!.radius < 15)
    }
    @Test fun solarCreationUsesThePhysicalScaleAndPilotedTurnsRedirectTheVelocity() {
        val game=SpaceGameState().apply { startSandbox() }
        game.chooseSpawnKind(BodyKind.Star)
        val star=game.previewBody(TouchPreview(Vec2(9000.0,0.0),Vec2(9000.0,0.0),0),0.0)!!
        assertTrue(star.physicalScale); assertTrue(star.mass > SolarBody.Earth.worldMass*1000)
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            var craft=star.copy(kind=kind,mass=1e-8,radius=1f,heading=Vec2(0.0,-1.0),velocity=Vec2(0.0,-500.0),pilotThrottle=0.0,
                fuelRemaining=vehicleFuelCapacity(kind))
            repeat(25) { index -> craft=steerManually(listOf(craft),ManualFlightControl(craft.id,1.0,if (index==0) 1.0 else 0.0),.02).single() }
            repeat(30) { craft=steerManually(listOf(craft),ManualFlightControl(craft.id,0.0),.02).single() }
            assertEquals(.5,craft.pilotThrottle,0.0)
            assertTrue(craft.velocity.x > 0 && craft.velocity.y > 0)
            val velocity=craft.velocity.normalized(); assertTrue(velocity.x*craft.heading.x+velocity.y*craft.heading.y > .99)
        }
    }
}
