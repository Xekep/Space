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
        assertEquals(-12.0,result.collisions.single().position.x,1e-8)
    }
    @Test fun absorptionDoesNotCreatePhantomSweepsForSurvivingVehicles() {
        val swallowed=target().copy(position=Vec2.Zero,velocity=Vec2.Zero)
        val ship=target().copy(id=3,position=Vec2(500.0,0.0),velocity=Vec2.Zero,kind=BodyKind.Ship,mass=1e-8,fuelRemaining=180.0)
        val rocket=ship.copy(id=4,position=Vec2(800.0,0.0),kind=BodyKind.Rocket)
        val result=SimulationEngine.stepSandbox(listOf(hole(),swallowed,ship,rocket),.001,0.0,false)
        assertEquals(listOf(1L,3L,4L),result.bodies.map { it.id })
        assertTrue(result.collisions.isEmpty())
    }
    @Test fun aFiniteBodyReachingTheHorizonIsSwallowedBeforeItsCenterCrosses() {
        val hole=hole()
        val grazing=target().copy(position=Vec2(0.0,11.0))
        val miss=grazing.copy(position=Vec2(0.0,13.0))
        assertEquals(1,absorbBlackHoles(listOf(hole,grazing),listOf(hole,grazing)).bodies.size)
        assertEquals(2,absorbBlackHoles(listOf(hole,miss),listOf(hole,miss)).bodies.size)
        val before=target().copy(position=Vec2(-100.0,11.0))
        val after=before.copy(position=Vec2(100.0,11.0))
        assertEquals(1,absorbBlackHoles(listOf(hole,before),listOf(hole,after)).bodies.size)
    }
    @Test fun spawnMassesPullStationaryBodiesInButAllowStableOrbitsWithMergingOff() {
        for (mass in listOf(12000.0,44000.0)) for (galaxy in listOf(false,true)) {
            val hole=hole(mass=mass)
            val resting=target().copy(position=Vec2(300.0,0.0),velocity=Vec2.Zero,mass=1.0,galaxyParticle=galaxy)
            var falling=listOf(hole,resting)
            var ticks=0
            while (falling.size > 1 && ticks++ < 1200)
                falling=SimulationEngine.stepSandbox(falling,1.0/120,0.0,false).bodies
            assertEquals("mass=$mass must capture a body released from rest",1,falling.size)
            assertEquals(mass+1,falling.single().mass,1e-8)
            val orbiting=resting.copy(velocity=SimulationEngine.orbitVelocity(hole,resting.position,resting.mass))
            var orbit=listOf(hole,orbiting)
            repeat(3600) {
                orbit=SimulationEngine.stepSandbox(orbit,1.0/120,0.0,false).bodies
                assertEquals(2,orbit.size)
                val distance=(orbit[0].position-orbit[1].position).magnitude()
                assertTrue("mass=$mass distance=$distance",distance in 298.0..302.0)
            }
            println("BLACK_HOLE mass=$mass galaxy=$galaxy infallSeconds=${ticks/120.0} orbitSeconds=30 stable=true")
        }
    }

}
