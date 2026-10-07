package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class GravitationalCollapseTest {
    @Test fun accretionCrossingTheThresholdCollapsesOnceAndPreservesMassAndMomentum() {
        for (mode in SandboxCollisionMode.entries) {
            val before=listOf(CelestialBody(1,Vec2(-8.0,0.0),Vec2(3.0,2.0),15000.0,10f,Color.Yellow,BodyKind.Star),
                CelestialBody(2,Vec2(8.0,0.0),Vec2(-1.0,-2.0),4000.0,10f,Color.Cyan))
            SimulationEngine.reserveBodyIds(before)
            val result=SimulationEngine.stepSandbox(before,.01,0.0,true,collisionMode=mode)
            val hole=result.bodies.single()
            assertEquals(BodyKind.BlackHole,hole.kind); assertEquals(19000.0,hole.mass,1e-8)
            assertEquals(41000.0,hole.velocity.x*hole.mass,1e-8)
            assertEquals(22000.0,hole.velocity.y*hole.mass,1e-8)
            val event=result.collisions.single { it.collapseRadius > 0 }
            val effects=advanceExplosions(emptyList(),listOf(event),0.0)
            assertEquals(1,effects.size); assertTrue(effects.single().particles.isEmpty())
            assertTrue(advanceExplosions(effects,emptyList(),1.5).isNotEmpty())
            assertTrue(advanceExplosions(effects,emptyList(),2.5).isEmpty())
            val next=SimulationEngine.stepSandbox(result.bodies,.01,0.0,true,collisionMode=mode)
            assertTrue(next.collisions.none { it.collapseRadius > 0 })
        }
    }
    @Test fun solarScaleUsesItsOwnThresholdAndUntouchedStarsDoNotCollapse() {
        val sun=CelestialBody(1,Vec2.Zero,Vec2.Zero,SolarBody.Sun.worldMass,SolarBody.Sun.worldRadius,Color.Yellow,BodyKind.Core,physicalScale=true)
        assertEquals(sun,collapseMassiveRemnants(listOf(sun),listOf(sun.copy(mass=sun.mass-1))).bodies.single())
        val heavy=sun.copy(mass=SOLAR_COLLAPSE_MASS+1)
        assertEquals(BodyKind.BlackHole,collapseMassiveRemnants(listOf(heavy),listOf(sun)).bodies.single().kind)
        assertEquals(heavy,collapseMassiveRemnants(listOf(heavy),listOf(heavy)).bodies.single())
        val small=sun.copy(mass=PLAYGROUND_COLLAPSE_MASS-1,physicalScale=false)
        assertEquals(small,collapseMassiveRemnants(listOf(small),listOf(small.copy(mass=100.0))).bodies.single())
    }
}
