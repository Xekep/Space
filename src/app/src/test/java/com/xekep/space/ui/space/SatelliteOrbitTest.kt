package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SatelliteOrbitTest {
    @Test fun physicallyImpossibleSubmoonIsRejectedRatherThanLaunchedAroundTheMainStar() {
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
        assertEquals(3,game.bodies.size)
        assertEquals(com.xekep.space.R.string.satellite_unstable, game.feedback)
        assertEquals(moon.id, game.orbitSourceId)
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
            assertEquals(SolarSystem.BODY_COUNT+1,game.bodies.size); assertNull(game.orbitSourceId)
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
            assertNotNull("Parent must survive",center)
            assertNotNull("Satellite must survive: radius=$radius, step=$it, parent=$parentId, satellite=$satelliteId",moon)
            val distance=(moon!!.position-center!!.position).magnitude()
            minimum=minOf(minimum,distance); maximum=maxOf(maximum,distance)
        }
        assertTrue("Orbit radius range: $minimum..$maximum",minimum > radius*(1-tolerance) && maximum < radius*(1+tolerance))
    }

    @Test fun newMoonsStayBoundToBothGasGiantsWithRingsAndArtificialSatellites() {
        for ((entry,radius) in listOf(SolarBody.Jupiter to 20.0,SolarBody.Saturn to 14.0)) {
            val game=SpaceGameState().apply { startSandbox() }
            val parent=game.bodies.first { it.solar == entry }
            game.selectBody(parent.id); game.prepareOrbit()
            val point=parent.position+Vec2(-radius,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(SolarSystem.BODY_COUNT+1,game.bodies.size)
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
        assertBound(game.bodies,parent.id,game.bodies.last().id,(game.bodies.last().position-parent.position).magnitude(),8.0,.12)
    }

    @Test fun wideOrbitIsNarrowedButOverlappingPlacementsAreStillRejected() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.BinaryStars) }
        val parent=game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val initial=game.bodies
        val far=parent.position+Vec2(-300.0,0.0)
        game.launch(TouchPreview(far,far,0),0.0)
        assertEquals(3,game.bodies.size); assertNull(game.feedback)
        val satellite=game.bodies.last()
        val radius=(satellite.position-parent.position).magnitude()
        assertTrue(radius < 150.0)
        assertBound(game.bodies,parent.id,satellite.id,radius,12.0,.12)
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
        assertBound(game.bodies,parent.id,game.bodies.last().id,(game.bodies.last().position-parent.position).magnitude(),8.0,.12)
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
    @Test fun satelliteRadiusUsesTheParentsDensityEvenForTinyBodies() {
        for ((mass,radius) in listOf(70.0 to 8f,1.0 to .4f,1e-8 to .00001f)) {
            val parent=CelestialBody(100000,Vec2.Zero,Vec2(180.0,-90.0),mass,radius,androidx.compose.ui.graphics.Color.Cyan)
            val game=SpaceGameState().apply {
                loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(parent),Vec2.Zero,1f,0.0,0))
            }
            var center=parent
            repeat(1) {
                game.selectBody(center.id); game.prepareOrbit()
                val point=center.position+Vec2(maxOf(center.radius*4.0,.1),0.0)
                val preview=TouchPreview(point,point,0)
                val candidate=game.previewBody(preview,4.0)!!
                assertTrue(candidate.mass < center.mass)
                assertTrue(candidate.radius > 0 && candidate.radius < center.radius)
                assertEquals(center.radius*cbrt(candidate.mass/center.mass),candidate.radius.toDouble(),center.radius*1e-6)
                assertEquals(SimulationEngine.orbitVelocity(center,point,candidate.mass),candidate.velocity)
                val count=game.bodies.size
                game.launch(preview,4.0)
                assertEquals(count+1,game.bodies.size)
                assertEquals(candidate.radius,game.bodies.last().radius)
                center=game.bodies.last()
            }
        }
    }

    @Test fun distantTapProducesABoundMoonAroundAMovingPlanetInsteadOfAnIndependentSolarOrbit() {
        val game=SpaceGameState().apply { startSandbox() }
        repeat(120) { game.update(1.0/60) }
        val parent=game.bodies.first { it.solar == SolarBody.Earth }
        game.selectBody(parent.id); game.prepareOrbit()
        val point=parent.position+Vec2(-50.0,0.0)
        val preview=TouchPreview(point,point,0)
        val candidate=game.previewBody(preview,0.0)!!
        val radius=(candidate.position-parent.position).magnitude()
        assertTrue(radius > parent.radius && radius < 4.0)
        assertEquals(SatellitePlacement.Clear,satellitePlacement(parent,candidate,game.bodies))
        game.launch(preview,0.0)
        assertEquals(candidate.position,game.bodies.last().position)
        assertBound(game.bodies,parent.id,game.bodies.last().id,radius,60.0,.15)
    }

    @Test fun theSolarMoonCanHostABoundSatelliteOnAnAutomaticallyNarrowedOrbit() {
        val game=SpaceGameState().apply { startSandbox() }
        var parent=game.bodies.first { it.solar == SolarBody.Moon }
        repeat(1) {
            game.selectBody(parent.id); game.prepareOrbit()
            val point=parent.position+Vec2(-.8,0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(SolarSystem.BODY_COUNT+1+it,game.bodies.size)
            val satellite=game.bodies.last()
            val radius=(satellite.position-parent.position).magnitude()
            assertTrue(radius < .8 && satellite.radius < parent.radius)
            assertBound(game.bodies,parent.id,satellite.id,radius,10.0,.2)
            parent=satellite
        }
    }

    @Test fun aSatelliteOfASatelliteStaysBoundWhenTheHierarchyHasRoomForBothOrbits() {
        val primary=CelestialBody(200000,Vec2.Zero,Vec2(200.0,-100.0),1.0,2f,
            androidx.compose.ui.graphics.Color.Yellow,physicalScale=true)
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(listOf(primary),Vec2.Zero,1f,0.0,0))
        }
        var parent=primary
        repeat(2) {
            game.selectBody(parent.id); game.prepareOrbit()
            val point=parent.position+Vec2(parent.radius*(if (it == 0) 16.0 else 4.0),0.0)
            game.launch(TouchPreview(point,point,0),0.0)
            assertEquals(it+2,game.bodies.size)
            val satellite=game.bodies.last()
            assertBound(game.bodies,parent.id,satellite.id,(satellite.position-parent.position).magnitude(),30.0,.15)
            parent=satellite
        }
    }

    @Test fun anOuterPlanetInFiveHundredBodiesKeepsAnAssistedSatelliteWithBarnesHutGravity() {
        var id=300000L
        val scene=RandomSystems.create(kotlin.random.Random(17)) { id++ }
        val star=scene.first { it.kind == BodyKind.BlackHole }
        val parent=scene.filter { it.kind == BodyKind.Ambient && it.mass > 5 &&
            (it.position-star.position).magnitude() < 5500 }.maxBy { (it.position-star.position).magnitude() }
        val game=SpaceGameState().apply {
            loadSandbox(com.xekep.space.storage.SandboxSnapshot(scene,Vec2.Zero,1f,0.0,0))
        }
        game.selectBody(parent.id); game.prepareOrbit()
        val point=parent.position+(parent.position-star.position).normalized()*300.0
        game.launch(TouchPreview(point,point,0),0.0)
        assertEquals(501,game.bodies.size)
        val satellite=game.bodies.last()
        val radius=(satellite.position-parent.position).magnitude()
        assertTrue(radius < 300.0)
        assertFalse(game.bodies.first { it.id == parent.id }.galaxyParticle)
        assertBound(game.bodies,parent.id,satellite.id,radius,30.0,.15)
        game.undo()
        assertEquals(500,game.bodies.size)
        assertTrue(game.bodies.first { it.id == parent.id }.galaxyParticle)
    }
}
