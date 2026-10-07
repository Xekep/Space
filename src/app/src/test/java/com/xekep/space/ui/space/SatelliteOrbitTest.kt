package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SatelliteOrbitTest {
    @Test fun satellitesOfSatellitesCanLaunchEvenWhenTheMainBodyPerturbsTheirOrbit() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val primary=game.bodies.first()
        game.selectBody(primary.id); game.prepareOrbit()
        val first=primary.position+Vec2(-80.0,0.0)
        game.launch(TouchPreview(first,first,0),0.0)
        assertEquals(3,game.bodies.size)
        val moon=game.bodies.last()
        game.selectBody(moon.id); game.prepareOrbit()
        val point=moon.position+Vec2(30.0,0.0)
        val preview=TouchPreview(point,point,0)
        assertEquals(SatellitePlacement.StrongTides,satellitePlacement(moon,game.previewBody(preview,0.0)!!,game.bodies))
        game.launch(preview,0.0)
        assertEquals("An unstable orbit must not prevent sandbox creation",4,game.bodies.size)
        val submoon=game.bodies.last()
        assertEquals(point,submoon.position)
        assertTrue(submoon.mass <= moon.mass*.02)
        assertNull(game.orbitSourceId)
        game.undo(); assertEquals(3,game.bodies.size)
    }

    @Test fun preparingSatelliteKeepsMovingParentStillAndResumesAfterLaunchOrCancel() {
        for (paused in listOf(false,true)) {
            val game=SpaceGameState().apply { startSandbox(); if (paused) toggleSandboxPause() }
            val earth=game.bodies.first { it.solar == SolarBody.Earth }
            game.selectBody(earth.id); game.prepareOrbit()
            val before=game.bodies
            repeat(120) { game.update(1.0/60) }
            assertEquals("The parent must not move while choosing an orbit",before,game.bodies)
            assertEquals(paused,game.sandbox!!.paused)
            val point=earth.position+Vec2(.35,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(18,game.bodies.size); assertNull(game.orbitSourceId)
            val launched=game.bodies
            game.update(1.0/60)
            if (paused) assertEquals(launched,game.bodies) else assertNotEquals(launched,game.bodies)
            game.selectBody(earth.id); game.prepareOrbit()
            val choosing=game.bodies
            game.clearSelection(); game.update(1.0/60)
            if (paused) assertEquals(choosing,game.bodies) else assertNotEquals(choosing,game.bodies)
            assertNull(game.feedback)
        }
    }

    @Test fun fastParentIsSelectedAtPointerDownAndItsSatelliteInheritsItsFullVelocity() {
        val parent=CelestialBody(9101,Vec2.Zero,Vec2(1400.0,-900.0),1000.0,10f,androidx.compose.ui.graphics.Color.Cyan)
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(parent),Vec2.Zero,4f,SimulationEngine.totalEnergy(listOf(parent)),0))
        }
        val tap=TouchPreview(parent.position,parent.position,0,tapBodyId=game.bodyAt(parent.position)!!.id)
        game.update(.2)
        assertTrue((game.bodies.single().position-parent.position).magnitude() > 100)
        game.finishGesture(tap,.2)
        assertEquals(parent.id,game.selectedBodyId); assertEquals(1,game.bodies.size)
        game.prepareOrbit()
        val center=game.orbitSource!!
        val point=center.position+Vec2(40.0,0.0)
        game.launch(TouchPreview(point,point,0),0.0)
        val moon=game.bodies.last()
        val orbitSpeed=SimulationEngine.orbitVelocity(center.copy(velocity=Vec2.Zero),point,moon.mass)
        assertEquals(center.velocity+orbitSpeed,moon.velocity)
        assertTrue("Satellite speed must not be clamped to the normal launch limit",moon.velocity.magnitude() > 1000)
        assertBound(game.bodies,parent.id,moon.id,40.0,8.0,.03)
    }

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

    @Test fun disturbedOrbitsWarnButOnlyOverlappingPlacementsAreRejected() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val parent=game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val initial=game.bodies
        val far=parent.position+Vec2(-300.0,0.0)
        game.launch(TouchPreview(far,far,0),0.0)
        assertEquals(3,game.bodies.size); assertEquals(com.xekep.space.R.string.satellite_unstable,game.feedback)
        assertNull(game.orbitSourceId)
        game.undo(); assertEquals(initial,game.bodies)
        game.selectBody(parent.id); game.prepareOrbit()
        val neighbor=game.bodies[1].position
        game.launch(TouchPreview(neighbor,neighbor,0),0.0)
        assertEquals(initial,game.bodies); assertEquals(com.xekep.space.R.string.satellite_farther,game.feedback)
        val safe=parent.position+Vec2(-80.0,0.0)
        game.launch(TouchPreview(safe,safe,0),0.0)
        assertEquals(3,game.bodies.size); assertNull(game.feedback)
        game.undo(); assertEquals(initial,game.bodies)
    }

    @Test fun placementProtectsParentFromCollisionsUntilCancelled() {
        val parent=CelestialBody(10001,Vec2.Zero,Vec2.Zero,70.0,8f,androidx.compose.ui.graphics.Color.Cyan)
        val larger=parent.copy(id=10002,mass=1000.0,radius=20f)
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(parent,larger),Vec2.Zero,1f,0.0,0,collisionsEnabled=true))
            selectBody(parent.id); prepareOrbit(); update(.02)
        }
        assertEquals(parent,game.orbitSource)
        assertEquals(2,game.bodies.size)
        game.clearSelection(); game.update(.02)
        assertEquals(1,game.bodies.size); assertNull(game.orbitSourceId)
        assertNull(game.feedback)
    }

    @Test fun largeSceneAlsoStaysStillWithTheBackgroundSolverDuringPlacement() = kotlinx.coroutines.runBlocking {
        val game=SpaceGameState().apply { startSandbox() }
        val marker=game.bodies.first().copy(id=10000,solar=null,physicalScale=true,mass=1e-8,radius=.00001f)
        val scene=game.bodies+List(80) { i -> marker.copy(id=10000L+i,position=Vec2(100000.0+i*100,100000.0)) }
        game.loadSandbox(com.xekep.space.storage.SandboxSnapshot(scene,Vec2.Zero,1f,0.0,0))
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        game.selectBody(earth.id); game.prepareOrbit()
        val before=game.bodies
        repeat(20) { game.updateSandboxAsync(1.0/60) }
        assertSame(before,game.bodies); assertFalse(game.sandbox!!.paused)
        game.clearSelection(); game.updateSandboxAsync(1.0/60)
        assertNotEquals(before,game.bodies)
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
