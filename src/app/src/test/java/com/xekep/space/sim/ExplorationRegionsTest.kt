package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test

class ExplorationRegionsTest {
    @Test fun regionGenerationIsStableVariedAndInitiallySeparated() {
        val state=ExplorationState(73,Vec2.Zero)
        var stars=0;var fields=0;val sizes=mutableListOf<Float>();val colors=mutableSetOf<androidx.compose.ui.graphics.Color>()
        for (x in 2..9) {
            val key=regionKey(x,2)
            val a=createExplorationRegion(state,key);val b=createExplorationRegion(state,key)
            assertEquals(a.map { it.copy(id=0,orbitParentId=null) },b.map { it.copy(id=0,orbitParentId=null) })
            assertTrue(a.size in 5..20);assertTrue(a.all { it.explorationRegion==key })
            for (i in a.indices) for (j in i+1 until a.size)
                assertTrue((a[i].position-a[j].position).magnitude()>a[i].radius+a[j].radius)
            stars+=a.count { it.kind==BodyKind.Star };fields+=a.count { it.isDebris }
            sizes+=a.filter { !it.isDebris }.map { it.radius };colors+=a.filter { it.kind==BodyKind.Star }.map { it.color }
        }
        assertTrue(stars>=3);assertTrue(fields>=30);assertTrue(colors.size>1)
        assertTrue(sizes.max()/sizes.min()>8)
    }
    @Test fun homeStaysCleanAndRevisitingDestroyedContentDoesNotRespawnIt() {
        val initial=ExplorationState(17,Vec2.Zero)
        assertFalse(exploreRegions(emptyList(),initial,Vec2.Zero,Vec2(1000.0,1000.0)).added)
        val focus=Vec2(6400.0,6400.0);val view=Vec2(1000.0,1000.0)
        val generated=exploreRegions(emptyList(),initial,focus,view)
        assertTrue(generated.added);assertEquals(1,generated.state.visited.size)
        var state=generated.state;var bodies=generated.bodies
        repeat(120) { val next=exploreRegions(bodies,state,focus,view);bodies=next.bodies;state=next.state }
        val keys=state.visited
        val emptied=exploreRegions(emptyList(),state,focus,view)
        assertFalse(emptied.added);assertEquals(keys,emptied.state.visited)
    }
    @Test fun regionalPhysicsBudgetLeavesStoredFarBodiesUnchanged() {
        val state=ExplorationState(17,Vec2.Zero)
        val keys=(2..11).map { regionKey(it,it) }
        val bodies=keys.flatMap { createExplorationRegion(state,it) }
        val frame=regionalFrame(bodies,state.copy(visited=keys.toSet()),Vec2(6400.0,6400.0))
        assertTrue(frame.active.size<=MAX_ACTIVE_EXPLORATION_BODIES)
        assertTrue(frame.dormant.isNotEmpty());assertEquals(bodies.size,frame.active.size+frame.dormant.size)
        val next=SimulationEngine.stepSandbox(frame.active,.02,0.0,false).bodies
        val combined=frame.combine(next)
        assertTrue(frame.dormant.all { it in combined })
    }
    @Test fun aShipOrNewSatelliteKeepsItsNearbyRegionAwakeEvenAwayFromCamera() {
        val state=ExplorationState(17,Vec2.Zero);val key=regionKey(7,7)
        val region=createExplorationRegion(state,key)
        val visitor=region.first().copy(id=SimulationEngine.newBodyId(),explorationRegion=null,kind=BodyKind.Ambient,mass=1.0)
        val frame=regionalFrame(region+visitor,state.copy(visited=setOf(key)),Vec2.Zero)
        assertEquals(region.size+1,frame.active.size);assertTrue(frame.dormant.isEmpty())
    }
    @Test fun generatedMoonsStayLocallyBoundInsteadOfImmediatelyEscapingToTheStar() {
        val state=ExplorationState(73,Vec2.Zero);var checked=0
        for (x in 2..7) {
            val initial=createExplorationRegion(state,regionKey(x,2))
            val moons=initial.filter { moon -> initial.any { it.id==moon.orbitParentId && it.kind!=BodyKind.Star } }
            var current=initial
            repeat(600) { current=SimulationEngine.stepSandbox(current,1.0/60,SimulationEngine.totalEnergy(initial),false).bodies }
            for (moon in moons) {
                val oldParent=initial.first { it.id==moon.orbitParentId };val parent=current.first { it.id==oldParent.id }
                val now=current.first { it.id==moon.id };val radius=(moon.position-oldParent.position).magnitude()
                assertTrue("cell=$x moon=${moon.id} parent=${parent.mass} radius=$radius now=${(now.position-parent.position).magnitude()}",(now.position-parent.position).magnitude() in radius*.6..radius*1.5);checked++
            }
        }
        assertTrue(checked>0)
    }

    @Test fun newRegionsStayOutsideCameraAtEveryZoomAndRotation() {
        val origin=Vec2(450.0,700.0)
        for (seed in listOf(17L,73L)) for (halfView in listOf(Vec2(540.0,1200.0),Vec2(3600.0,7800.0),Vec2(108000.0,234000.0)))
            for (angle in listOf(0.0,.7,Math.PI)) {
                val focus=origin+Vec2(4200.0,0.0);var state=ExplorationState(seed,origin);var bodies=emptyList<CelestialBody>()
                repeat(8) {
                    val found=exploreRegions(bodies,state,focus,halfView,angle)
                    assertTrue(found.added)
                    for (body in found.bodies.drop(bodies.size)) {
                        val delta=body.position-focus;val x=delta.x*kotlin.math.cos(angle)-delta.y*kotlin.math.sin(angle)
                        val y=delta.x*kotlin.math.sin(angle)+delta.y*kotlin.math.cos(angle)
                        val halo=body.radius*(if(body.kind==BodyKind.Star) 3.4 else 1.0)
                        assertTrue("body=${body.id} view=$halfView angle=$angle",kotlin.math.abs(x)>halfView.x+halo || kotlin.math.abs(y)>halfView.y+halo)
                    }
                    bodies=found.bodies;state=found.state
                }
            }
    }
    @Test fun regionEnvelopeContainsPlanetsMoonsJunkAndGlows() {
        for(seed in 1L..100L) for (key in listOf(regionKey(1,0),regionKey(2,2))) {
            val state=ExplorationState(seed,Vec2.Zero);val center=explorationRegionCenter(state,key)
            assertTrue(createExplorationRegion(state,key).all {
                (it.position-center).magnitude()+it.radius*(if(it.kind==BodyKind.Star) 3.4 else 1.0)<1200
            })
        }
    }
    @Test fun systemsArePreloadedBeforeCameraArrivesAndRevisitingDoesNotDuplicateThem() {
        val origin=Vec2(450.0,700.0);val state=ExplorationState(17,origin);val view=Vec2(540.0,1200.0)
        val found=exploreRegions(emptyList(),state,origin+Vec2(2000.0,0.0),view)
        assertTrue(found.added);assertTrue(found.bodies.all { (it.position-origin).magnitude()>2500 })
        val center=explorationRegionCenter(state,found.state.visited.single())
        val arrived=exploreRegions(found.bodies,found.state,center,view)
        assertTrue(arrived.bodies.containsAll(found.bodies))
        assertEquals(arrived.bodies.size,arrived.bodies.map { it.id }.distinct().size)
    }
}
