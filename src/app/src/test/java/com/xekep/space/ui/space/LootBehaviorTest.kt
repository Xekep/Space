package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LootBehaviorTest {
    private fun run()=ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),SpaceCamera(),IntSize(900,1400),ArcadeDifficulty.Normal,spawnTimer=1000.0)
    @Test fun randomTrophyLooksCoverAllThreeTypesWithoutConsumingAnotherCombatRandomDraw() {
        val appearances=mutableSetOf<LootAppearance>()
        repeat(60) { seed ->
            val random=Random(seed);val reference=Random(seed)
            val phase=reference.nextDouble(2*kotlin.math.PI)
            val prepared=prepareCampaign(run().copy(elapsed=168.0),random)
            assertEquals(reference.nextInt(),random.nextInt())
            val loot=prepared.salvage!!;appearances+=loot.appearance
            assertEquals(LootAppearance.entries[(phase/(2*kotlin.math.PI)*3).toInt()],loot.appearance)
        }
        assertEquals(LootAppearance.entries.toSet(),appearances)
    }
    @Test fun bothCargoAndTrophyDriftInWorldSpaceAndStayCollectedExactlyOnce() {
        val run=run();val point=Vec2(1200.0,700.0)
        val trophy=run.copy(salvage=ArcadeSalvage(7,point,appearance=LootAppearance.Module))
        val drift=finishCampaign(trophy,trophy,1.0,null)
        assertEquals(point+lootDriftVelocity(7),drift.salvage!!.position)
        val event=run.copy(sideEvent=ArcadeSideEvent(SideEventKind.Supply,point))
        val moved=advanceSideEvent(event,event,1.0,null)
        assertEquals(point+lootDriftVelocity(40),moved.sideEvent!!.position)
        val ship=CelestialBody(999,point,Vec2.Zero,36.0,6f,Color.Cyan,BodyKind.Ship)
        val collect=advanceSideEvent(event,event.copy(bodies=event.bodies+ship),.1,999)
        assertEquals(SideEventStatus.Completed,collect.sideEvent!!.status)
        val repeated=advanceSideEvent(collect,collect,1.0,999)
        assertEquals(collect.energy,repeated.energy,0.0);assertEquals(collect.eventsCompleted,repeated.eventsCompleted)
    }
}
