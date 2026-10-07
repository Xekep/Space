package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CollisionBroadPhaseTest {
    @Test fun sweepRetainsEveryExactContactForRotatingVehiclesAndMovingCelestialBodies() {
        val random=Random(28)
        repeat(12) { seed ->
            val before=List(200) { i ->
                CelestialBody(i.toLong(),Vec2(random.nextDouble(-300.0,300.0),random.nextDouble(-300.0,300.0)),Vec2.Zero,
                    70.0,random.nextDouble(.01,16.0).toFloat(),Color.Cyan,BodyKind.entries[i%BodyKind.entries.size],
                    heading=Vec2(random.nextDouble(-1.0,1.0),random.nextDouble(-1.0,1.0)).normalized())
            }
            val after=before.map { it.copy(position=it.position+Vec2(random.nextDouble(-70.0,70.0),random.nextDouble(-70.0,70.0)),
                heading=Vec2(random.nextDouble(-1.0,1.0),random.nextDouble(-1.0,1.0)).normalized()) }
            val candidates=collisionPairs(after,before).toSet()
            for (i in before.indices) for (j in i+1 until before.size) {
                if (bodyContact(before[i],after[i],before[j],after[j]) != null)
                    assertTrue("Missed swept contact $seed: $i/$j",i to j in candidates)
            }
        }
    }
    @Test fun verticalSparseColumnAndHighSpeedCrossingUseOnlyNearbyCandidates() {
        val before=List(500) { i -> CelestialBody(i.toLong(),Vec2(0.0,i*40.0),Vec2.Zero,70.0,1f,Color.Cyan) }
        assertTrue(collisionPairs(before,before).isEmpty())
        val start=before+CelestialBody(1000,Vec2(-1000.0,400.0),Vec2.Zero,1.0,1f,Color.Cyan,BodyKind.Rocket,heading=Vec2(1.0,0.0))
        val end=start.dropLast(1)+start.last().copy(position=Vec2(1000.0,400.0))
        val pairs=collisionPairs(end,start)
        assertEquals(listOf(10 to 500),pairs)
        assertNotNull(bodyContact(start[10],end[10],start.last(),end.last()))
    }
}
