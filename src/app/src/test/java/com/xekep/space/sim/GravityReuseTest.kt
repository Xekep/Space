package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GravityReuseTest {
    @Test fun reusedForcesMatchFreshTreesIncludingEditedMassesPositionsScalesAndCounts() {
        var id=0L
        var cached=RandomSystems.create(Random(99)) { ++id }.take(180)
        var fresh=cached
        repeat(30) { step ->
            fun edit(bodies: List<CelestialBody>): List<CelestialBody> = when (step) {
                5 -> bodies.mapIndexed { i,b -> if (i == 0) b.copy(mass=b.mass*1.5) else b }
                10 -> bodies.mapIndexed { i,b -> if (i == 1) b.copy(position=b.position+Vec2(20.0,30.0)) else b }
                15 -> bodies.mapIndexed { i,b -> if (i == 2) b.copy(physicalScale=true) else b }
                20 -> bodies.dropLast(1)
                else -> bodies
            }
            cached=NumericIntegrator.advance(edit(cached),1.0/60,1.0/240)
            fresh=NumericIntegrator.advance(edit(fresh),1.0/60,1.0/240,cacheGravity=false)
            assertEquals(fresh,cached)
        }
    }
}
