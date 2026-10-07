package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class MassCollisionStressTest {
    private fun scene()=List(500) { i ->
        val pair=i/2; val sign=if (i%2 == 0) -1.0 else 1.0
        val position=Vec2((pair%25-12)*65.0+sign*6,(pair/25-5)*65.0)
        CelestialBody(i.toLong()+1,position,Vec2(-sign*150,0.0),70.0,4f,Color.Cyan,
            trail=List(42) { position+Vec2(sign*(41-it)*2,0.0) })
    }
    @Test fun twoHundredFiftySimultaneousImpactsKeepMassMomentumAndBoundNewPhysicalFragments() {
        val initial=scene(); SimulationEngine.reserveBodyIds(initial)
        val energy=SimulationEngine.totalEnergy(initial)
        val first=SimulationEngine.stepSandbox(initial,1.0/30,energy,true,collisionMode=SandboxCollisionMode.Debris)
        assertTrue("The stress scene must actually collide",first.collisions.size >= 200)
        assertTrue(first.bodies.size in 502..516)
        assertEquals(initial.sumOf { it.mass },first.bodies.sumOf { it.mass },1e-7)
        val p=first.bodies.fold(Vec2.Zero) { total,b -> total+b.velocity*b.mass }
        assertEquals(0.0,p.x,1e-6); assertEquals(0.0,p.y,1e-6)
        assertTrue(first.bodies.filterNot { it.isDebris }.all { it.trail.size <= 7 })
        var bodies=first.bodies
        repeat(60) {
            val previous=bodies.size
            bodies=SimulationEngine.stepSandbox(bodies,1.0/30,energy,true,collisionMode=SandboxCollisionMode.Debris).bodies
            assertTrue(bodies.size <= previous+16 && bodies.size <= 1000)
            assertEquals(initial.sumOf { it.mass },bodies.sumOf { it.mass },1e-6)
            assertEquals(bodies.size,bodies.map { it.id }.distinct().size)
            assertTrue(bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() })
        }
        assertTrue(bodies.filter { it.isDebris }.all { it.trail.size <= 8 })
        assertTrue(bodies.filterNot { it.isDebris }.all { it.trail.size <= 20 })
    }
    @Test fun tinyRestingContactsDoNotBounceOrCreateAStormOfHitEffects() {
        val previous=listOf(CelestialBody(1,Vec2(-4.0,0.0),Vec2(.01,0.0),70.0,4f,Color.Cyan),
            CelestialBody(2,Vec2(4.0,0.0),Vec2(-.01,0.0),70.0,4f,Color.Yellow))
        val after=previous.map { it.copy(position=it.position+it.velocity*.01) }
        val result=debrisCollisions(after,previous,.01) { error("A tiny contact must not fragment") }
        assertTrue(result.collisions.isEmpty())
        assertEquals(0.0,result.bodies[0].velocity.magnitude(),1e-9)
        assertEquals(0.0,result.bodies[1].velocity.magnitude(),1e-9)
    }
    @Test fun mergedBodiesStartTheirOwnTailRatherThanConnectingUnrelatedHistories() {
        val bodies=scene().take(2)
        val merged=SimulationEngine.stepSandbox(bodies,.02,SimulationEngine.totalEnergy(bodies),true).bodies.single()
        assertTrue(merged.trail.size <= 2); assertEquals(merged.position,merged.trail.last())
    }
}
