package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeSideEventsTest {
    private fun run(wave: Int=4)=ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),SpaceCamera(),IntSize(900,1400),ArcadeDifficulty.Normal,elapsed=(wave-1)*28.0)
    private fun ship(p: Vec2)=CelestialBody(SimulationEngine.newBodyId(),p,Vec2.Zero,36.0,6f,Color.Cyan,BodyKind.Ship)
    @Test fun eventScheduleIsSeededVariedAndAvoidsMajorMilestones() {
        val events=(1..20).map { prepareSideEvent(run().copy(eventRandomState=it.toLong()),Random(it)) }
        assertEquals(3,events.map { it.sideEvent!!.kind }.toSet().size)
        assertTrue(events.all { it.nextEventWave in 7..9 })
        assertEquals(prepareSideEvent(run(),Random(73)).sideEvent,prepareSideEvent(run(),Random(73)).sideEvent)
        for (wave in listOf(1,7,10,13,15,20)) assertNull(prepareSideEvent(run(wave),Random(4)).sideEvent)
        val previous=prepareSideEvent(run(),Random(4));assertEquals(previous,prepareSideEvent(previous,Random(99)))
        val later=previous.copy(elapsed=8*28.0,sideEvent=previous.sideEvent!!.copy(status=SideEventStatus.Missed))
        assertNotEquals(previous.sideEvent!!.kind,prepareSideEvent(later,Random(4)).sideEvent!!.kind)
    }
    @Test fun aSupplyCrossingNeedsIntentionalControlAndPaysOnlyOnce() {
        val p=Vec2(1000.0,700.0);val body=ship(p+Vec2(-100.0,0.0))
        val before=run().copy(bodies=run().bodies+body,energy=0.0,sideEvent=ArcadeSideEvent(SideEventKind.Supply,p))
        val after=before.copy(bodies=before.bodies.map { if(it.id==body.id) it.copy(position=p+Vec2(100.0,0.0)) else it })
        assertEquals(SideEventStatus.Active,advanceSideEvent(before,after,1.0,null).sideEvent!!.status)
        val collected=advanceSideEvent(before,after,1.0,body.id)
        assertEquals(SideEventStatus.Completed,collected.sideEvent!!.status);assertEquals(45.0,collected.energy,0.0);assertEquals(1,collected.eventsCompleted)
        assertEquals(collected,advanceSideEvent(collected,collected,1.0,body.id))
    }
    @Test fun rescueWaitsForEscortCanBeLostAndNeverCostsCoreHullOnTimeout() {
        val run=run();val core=run.bodies.first();val p=core.position+Vec2(500.0,0.0)
        val waiting=run.copy(sideEvent=ArcadeSideEvent(SideEventKind.Rescue,p))
        assertEquals(p,advanceSideEvent(waiting,waiting,1.0,null).sideEvent!!.position)
        val body=ship(p);val escorted=waiting.copy(bodies=waiting.bodies+body)
        val moved=advanceSideEvent(escorted,escorted,1.0,body.id)
        assertEquals(65.0,(moved.sideEvent!!.position-p).magnitude(),1e-7)
        val expired=advanceSideEvent(waiting,waiting,40.0,null)
        assertEquals(SideEventStatus.Missed,expired.sideEvent!!.status);assertEquals(run.lives,expired.lives)
        val meteor=body.copy(kind=BodyKind.Meteor)
        val hit=waiting.copy(bodies=waiting.bodies+meteor)
        assertEquals(SideEventStatus.Missed,advanceSideEvent(hit,hit,.1,null).sideEvent!!.status)
    }
    @Test fun recoveredCraftRespectsCapacityAndReturnsEnergyIfTheFleetIsFull() {
        val run=run();val p=run.bodies.first().position+Vec2(600.0,0.0)
        val visitor=ship(p+Vec2(45.0,0.0))
        val event=run.copy(bodies=run.bodies+visitor,sideEvent=ArcadeSideEvent(SideEventKind.Wreck,p),energy=0.0)
        val next=advanceSideEvent(event,event,.1,visitor.id)
        assertEquals(2,next.bodies.count { it.isVehicle });assertEquals(1,next.launches)
        val full=event.copy(bodies=event.bodies+listOf(ship(p+Vec2(300.0,0.0)),ship(p+Vec2(400.0,0.0))))
        val capped=advanceSideEvent(full,full,.1,visitor.id)
        assertEquals(3,capped.bodies.count { it.isVehicle });assertEquals(45.0,capped.energy,0.0)
    }
    @Test fun optionalEventsNeverConsumeTheWaveRandomStream() {
        val random=SessionRandom(17);val before=random.state
        val event=prepareSideEvent(run(),random)
        assertNotNull(event.sideEvent);assertEquals(before,random.state);assertNotEquals(before,event.eventRandomState)
        val repeat=prepareSideEvent(event.copy(elapsed=8*28.0,sideEvent=event.sideEvent!!.copy(status=SideEventStatus.Missed)),random)
        assertEquals(before,random.state);assertNotEquals(event.eventRandomState,repeat.eventRandomState)
    }

}
