package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class DebrisCollisionsTest {
    private fun pair() = listOf(
        CelestialBody(1,Vec2(-11.0,0.0),Vec2(80.0,0.0),100.0,10f,Color.Cyan),
        CelestialBody(2,Vec2(11.0,0.0),Vec2(-80.0,0.0),100.0,10f,Color.Yellow))
    private fun collide(bodies: List<CelestialBody>): StepResult {
        var id=(bodies.maxOfOrNull { it.id } ?: 0)+1
        val moved=bodies.map { it.copy(position=it.position+it.velocity*.02) }
        return debrisCollisions(moved,bodies,.02) { id++ }
    }
    private fun verifyConserved(before: List<CelestialBody>,after: List<CelestialBody>) {
        assertEquals(before.sumOf { it.mass },after.sumOf { it.mass },1e-8)
        fun momentum(bodies: List<CelestialBody>)=bodies.fold(Vec2.Zero) { p,b -> p+b.velocity*b.mass }
        assertEquals(momentum(before).x,momentum(after).x,1e-8); assertEquals(momentum(before).y,momentum(after).y,1e-8)
        assertTrue(after.sumOf { it.mass*it.velocity.magnitude()*it.velocity.magnitude() } <=
            before.sumOf { it.mass*it.velocity.magnitude()*it.velocity.magnitude() }+1e-6)
    }
    @Test fun energeticImpactTransfersMomentumAndCreatesRealMassConservingFragments() {
        val before=pair().map { it.copy(velocity=it.velocity+Vec2(25.0,-12.0)) }
        val result=collide(before)
        assertEquals(8,result.bodies.size); assertTrue(result.collisions.single().debrisImpact)
        verifyConserved(before,result.bodies)
        assertTrue(result.bodies.drop(2).all { it.radius < 10 && it.kind == BodyKind.Ambient })
        assertTrue(result.bodies[0].velocity.x < 25 && result.bodies[1].velocity.x > 25)
    }
    @Test fun gentleImpactBouncesWithoutSheddingMass() {
        val before=pair().map { it.copy(position=it.position*.9,velocity=it.velocity*.03) }
        val result=collide(before)
        assertEquals(2,result.bodies.size); verifyConserved(before,result.bodies)
        assertTrue(result.bodies[0].velocity.x < 0 && result.bodies[1].velocity.x > 0)
    }
    @Test fun sceneLimitDoesNotLoseMassOrOverflowTheSaveLimit() {
        for (count in listOf(995,999,1000)) {
            val before=pair()+List(count-2) { i -> CelestialBody(3L+i,Vec2(1000.0+i*50,1000.0),Vec2.Zero,.1,1f,Color.Gray) }
            val result=collide(before)
            assertTrue(result.bodies.size <= 1000); verifyConserved(before,result.bodies)
        }
    }
    @Test fun mergeDebrisAndDisabledCollisionModesRemainDistinct() {
        val before=pair(); val energy=SimulationEngine.totalEnergy(before)
        assertEquals(1,SimulationEngine.stepSandbox(before,.02,energy,true).bodies.size)
        assertTrue(SimulationEngine.stepSandbox(before,.02,energy,true,collisionMode=SandboxCollisionMode.Debris).bodies.size > 2)
        assertEquals(2,SimulationEngine.stepSandbox(before,.02,energy,false,collisionMode=SandboxCollisionMode.Debris).bodies.size)
    }
    @Test fun fastSweepsAndPhysicalScaleImpactsAreNotMissed() {
        val before=pair().map { it.copy(position=it.position/100.0,velocity=it.velocity*10.0,radius=.01f,physicalScale=true) }
        val result=collide(before)
        assertEquals(8,result.bodies.size); verifyConserved(before,result.bodies)
        assertTrue(result.bodies.all { it.physicalScale && it.position.x.isFinite() && it.radius > 0 })
    }
}
