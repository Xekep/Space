package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class BlackHoleTest {
    private fun hole(id: Long=1,mass: Double=12000.0) = CelestialBody(id,Vec2.Zero,Vec2.Zero,mass,10f,Color.Magenta,BodyKind.BlackHole)
    private fun target() = CelestialBody(2,Vec2(-100.0,0.0),Vec2(50.0,0.0),100.0,2f,Color.Cyan)
    @Test fun sweptHorizonAbsorbsFastBodiesAndPreservesMassAndMomentum() {
        val hole=hole(); val target=target()
        val result=absorbBlackHoles(listOf(hole,target),listOf(hole,target.copy(position=Vec2(100.0,0.0))))
        val remaining=result.bodies.single()
        assertEquals(hole.id,remaining.id); assertEquals(12100.0,remaining.mass,0.0)
        assertEquals(5000.0,remaining.velocity.x*remaining.mass,1e-8)
        assertTrue(remaining.radius > hole.radius)
    }
    @Test fun outsideTheHorizonIsNotAbsorbedAndGravityWorksWithMergingOff() {
        val outside=target().copy(position=Vec2(40.0,0.0),velocity=Vec2.Zero)
        assertEquals(2,absorbBlackHoles(listOf(hole(),outside),listOf(hole(),outside)).bodies.size)
        val run=SimulationEngine.stepSandbox(listOf(hole(),outside),.001,0.0,false)
        assertEquals(2,run.bodies.size); assertTrue(run.bodies.first { it.id == outside.id }.velocity.x < 0)
        val inside=outside.copy(position=Vec2(2.0,0.0))
        assertEquals(1,SimulationEngine.stepSandbox(listOf(hole(),inside),.001,0.0,false).bodies.size)
    }
    @Test fun swallowedVehiclesExplodeAndTheirMassJoinsTheHole() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val vehicle=target().copy(kind=kind,position=Vec2.Zero)
            val result=absorbBlackHoles(listOf(hole(),vehicle),listOf(hole(),vehicle))
            assertEquals(12100.0,result.bodies.single().mass,0.0)
            assertTrue(result.collisions.single().vehicleExplosion)
        }
    }
    @Test fun overlappingHolesHaveOneDeterministicSurvivorWithoutDoubleCounting() {
        val bodies=listOf(hole(1,12000.0),hole(2,20000.0),target().copy(id=3,position=Vec2.Zero))
        val result=absorbBlackHoles(bodies,bodies).bodies.single()
        assertEquals(2L,result.id); assertEquals(32100.0,result.mass,0.0)
        assertEquals(result,absorbBlackHoles(bodies.reversed(),bodies.reversed()).bodies.single())
    }
    @Test fun chainedAbsorptionRetainsNewlyGainedMassAndFastVehiclesExplodeAtTheHorizon() {
        val large=hole(1,20000.0)
        val wide=hole(3,12000.0).copy(position=Vec2(15.0,0.0),radius=20f)
        val bodies=listOf(large,wide,target().copy(position=Vec2.Zero))
        assertEquals(32100.0,absorbBlackHoles(bodies,bodies).bodies.single().mass,0.0)
        val rocket=target().copy(kind=BodyKind.Rocket)
        val result=absorbBlackHoles(listOf(large,rocket),listOf(large,rocket.copy(position=Vec2(100.0,0.0))))
        assertEquals(-10.0,result.collisions.single().position.x,1e-8)
    }
    @Test fun absorptionDoesNotCreatePhantomSweepsForSurvivingVehicles() {
        val swallowed=target().copy(position=Vec2.Zero,velocity=Vec2.Zero)
        val ship=target().copy(id=3,position=Vec2(500.0,0.0),velocity=Vec2.Zero,kind=BodyKind.Ship,mass=1e-8)
        val rocket=ship.copy(id=4,position=Vec2(800.0,0.0),kind=BodyKind.Rocket)
        val result=SimulationEngine.stepSandbox(listOf(hole(),swallowed,ship,rocket),.001,0.0,false)
        assertEquals(listOf(1L,3L,4L),result.bodies.map { it.id })
        assertTrue(result.collisions.isEmpty())
    }
}
