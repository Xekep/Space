package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SatelliteOrbitTest {
    private fun assertBound(initial: List<CelestialBody>,parentId: Long,satelliteId: Long,radius: Double,seconds: Double,tolerance: Double) {
        var scene=initial
        var minimum=Double.POSITIVE_INFINITY; var maximum=0.0
        repeat((seconds*120).roundToInt()) {
            scene=SimulationEngine.stepSandbox(scene,1.0/120,0.0,true).bodies
            val center=scene.firstOrNull { it.id == parentId }; val moon=scene.firstOrNull { it.id == satelliteId }
            assertNotNull("Parent must survive",center); assertNotNull("Satellite must survive",moon)
            val distance=(moon!!.position-center!!.position).magnitude()
            minimum=minOf(minimum,distance); maximum=maxOf(maximum,distance)
        }
        assertTrue("Orbit radius range: $minimum..$maximum",minimum > radius*(1-tolerance) && maximum < radius*(1+tolerance))
    }

    @Test fun newMoonsStayBoundToBothGasGiantsWithAllSeventeenExistingBodies() {
        for ((entry,radius) in listOf(SolarBody.Jupiter to 20.0,SolarBody.Saturn to 14.0)) {
            val game=SpaceGameState().apply { startSandbox() }
            val parent=game.bodies.first { it.solar == entry }
            game.selectBody(parent.id); game.prepareOrbit()
            val point=parent.position+Vec2(-radius,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(18,game.bodies.size)
            assertBound(game.bodies,parent.id,game.bodies.last().id,radius,30.0,.08)
        }
    }

    @Test fun movingBinaryParentKeepsItsSatelliteInAnEightyBodyScene() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val boost=Vec2(120.0,-80.0)
        val scene=game.bodies.map { it.copy(velocity=it.velocity+boost) }+
            List(80) { i -> CelestialBody(10000L+i,Vec2(5000.0+i*60,5000.0),boost,.05,.1f,androidx.compose.ui.graphics.Color.Cyan) }
        game.loadSandbox(com.xekep.space.storage.SandboxSnapshot(scene,Vec2.Zero,1f,0.0,0,collisionsEnabled=true,preset=SandboxPresetKind.BinaryStars))
        val parent=game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val point=parent.position+Vec2(-80.0,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        assertEquals(83,game.bodies.size)
        assertBound(game.bodies,parent.id,game.bodies.last().id,80.0,8.0,.12)
    }

    @Test fun disturbedAndOverlappingPlacementsAreRejectedAndCanBeRetried() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val parent=game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val initial=game.bodies
        val far=parent.position+Vec2(-300.0,0.0)
        game.launch(TouchPreview(far,far,0),0.0)
        assertEquals(initial,game.bodies); assertEquals(com.xekep.space.R.string.satellite_unstable,game.feedback)
        assertEquals(parent.id,game.orbitSourceId)
        val neighbor=game.bodies[1].position
        game.launch(TouchPreview(neighbor,neighbor,0),0.0)
        assertEquals(initial,game.bodies); assertEquals(com.xekep.space.R.string.satellite_farther,game.feedback)
        val safe=parent.position+Vec2(-80.0,0.0)
        game.launch(TouchPreview(safe,safe,0),0.0)
        assertEquals(3,game.bodies.size); assertNull(game.feedback)
        game.undo(); assertEquals(initial,game.bodies)
    }

    @Test fun losingTheParentCancelsPlacementInsteadOfLaunchingAnUnassistedBody() {
        val parent=CelestialBody(10001,Vec2.Zero,Vec2.Zero,70.0,8f,androidx.compose.ui.graphics.Color.Cyan)
        val larger=parent.copy(id=10002,mass=1000.0,radius=20f)
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(parent,larger),Vec2.Zero,1f,0.0,0,collisionsEnabled=true))
            selectBody(parent.id); prepareOrbit(); update(.02)
        }
        assertNull(game.orbitSource)
        val before=game.bodies
        val point=Vec2(300.0,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        assertEquals(before,game.bodies); assertNull(game.orbitSourceId)
        assertEquals(com.xekep.space.R.string.satellite_parent_lost,game.feedback)
    }

    @Test fun mixedPhysicalAndPlaygroundScalesUseTheSameGravityAsTheIntegrator() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val marker=CelestialBody(10003,Vec2(100000.0,100000.0),Vec2.Zero,1e-8,.00001f,
            androidx.compose.ui.graphics.Color.Cyan,physicalScale=true)
        game.loadSandbox(com.xekep.space.storage.SandboxSnapshot(game.bodies+marker,Vec2.Zero,1f,0.0,0))
        val parent=game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val point=parent.position+Vec2(-80.0,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        assertTrue(game.bodies.last().physicalScale)
        assertBound(game.bodies,parent.id,game.bodies.last().id,80.0,8.0,.12)
    }
    @Test fun closeEarthSatelliteRemainsBoundInTheFullSolarSystem() {
        val game=SpaceGameState().apply { startSandbox() }
        val parent=game.bodies.first { it.solar == SolarBody.Earth }
        game.selectBody(parent.id); game.prepareOrbit()
        val point=parent.position+Vec2(.2,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        val satellite=game.bodies.last()
        assertTrue(satellite.id != parent.id)
        var scene=game.bodies
        var minimum=Double.POSITIVE_INFINITY; var maximum=0.0
        repeat(1200) {
            scene=SimulationEngine.stepSandbox(scene,1.0/120,0.0,true).bodies
            val center=scene.firstOrNull { it.id == parent.id }
            val moon=scene.firstOrNull { it.id == satellite.id }
            assertNotNull("Parent must survive",center)
            assertNotNull("Satellite must not fall into Earth",moon)
            val distance=(moon!!.position-center!!.position).magnitude()
            minimum=minOf(minimum,distance); maximum=maxOf(maximum,distance)
        }
        assertTrue("Orbit radius range: $minimum..$maximum",minimum > .18 && maximum < .22)
    }
}
