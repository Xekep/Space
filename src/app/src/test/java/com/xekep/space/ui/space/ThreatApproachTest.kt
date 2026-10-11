package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ThreatApproachTest {
    @Test fun entryArrowUsesTheIncomingPathInsteadOfTheDirectionFromScreenCenter() {
        assertEquals(Vec2(0.0,360.0),incomingScreenEntry(Vec2(-100.0,400.0),Vec2(50.0,-20.0),1000.0,800.0))
        assertEquals(Vec2(200.0,0.0),incomingScreenEntry(Vec2(200.0,-100.0),Vec2(0.0,50.0),1000.0,800.0))
        assertEquals(Vec2.Zero,incomingScreenEntry(Vec2(-100.0,-100.0),Vec2(50.0,50.0),1000.0,800.0))
        assertNull(incomingScreenEntry(Vec2(-100.0,400.0),Vec2(-50.0,0.0),1000.0,800.0))
        assertNull(incomingScreenEntry(Vec2(-100.0,-100.0),Vec2(50.0,0.0),1000.0,800.0))
    }
    @Test fun liveThreatsHaveAnEdgeArrowOrAVisibleWormholeAndMoveImmediately() {
        for (zoom in listOf(.08f,.15f,1f,6f)) for (rotation in listOf(0.0,1.2))
        for (center in listOf(Vec2(450.0,700.0),Vec2(600.0,-200.0),Vec2(8000.0,8000.0))) {
            val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
            val run=ArcadeSession(listOf(core),SpaceCamera(center,zoom),IntSize(900,1400),ArcadeDifficulty.Normal,spawnTimer=0.0)
            val view=ThreatView(IntSize(1080,2340),rotation)
            val spawned=advanceArcade(run,.01,Random(2),view=view)
            assertTrue(spawned.pending.isEmpty())
            val meteor=spawned.bodies.single { it.kind == BodyKind.Meteor }
            val point=worldToScreen(meteor.position,view.viewport,center,zoom,rotation)
            val bounds=androidx.compose.ui.geometry.Rect(24f,160f,1056f,2180f)
            val marker=threatDirectionMarker(point,rotateVector(meteor.velocity*zoom.toDouble(),rotation),meteor.radius*zoom,view.viewport,bounds)
            if (marker != null) assertTrue(spawned.arrivalPortals.isEmpty())
            else {
                assertEquals(1,spawned.arrivalPortals.size)
                assertEquals(meteor.position,spawned.arrivalPortals.single().position)
            }
            val flying=advanceArcade(spawned.copy(spawnTimer=1000.0),.1,Random(2),view=view).bodies.first { it.id == meteor.id }
            assertTrue((flying.position-core.position).magnitude()<(meteor.position-core.position).magnitude())
        }
    }
    @Test fun cameraChangesCannotRepositionOrDelayAnAlreadyFlyingThreat() {
        val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
        val run=ArcadeSession(listOf(core),SpaceCamera(core.position),IntSize(900,1400),ArcadeDifficulty.Normal,spawnTimer=0.0)
        val first=advanceArcade(run,.01,Random(17),view=ThreatView(IntSize(1080,2340)))
        val target=first.bodies.single { it.kind == BodyKind.Meteor }.id
        val next=advanceArcade(first.copy(spawnTimer=1000.0),.1,Random(17))
        val moved=advanceArcade(first.copy(spawnTimer=1000.0,camera=SpaceCamera(Vec2(9999.0,-9999.0),.08f)),.1,Random(17),view=ThreatView(IntSize(720,1280),1.4))
        assertEquals(next.bodies.first { it.id==target },moved.bodies.first { it.id==target })
    }
    @Test fun oldSavedWarningIsPromotedExactlyOnceAndStartsMovingWithoutACountdown() {
        val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
        val threat=core.copy(id=999,kind=BodyKind.Meteor,mass=90.0,radius=10f,position=Vec2(2000.0,700.0),velocity=Vec2(-100.0,0.0))
        val run=ArcadeSession(listOf(core),SpaceCamera(core.position),IntSize(900,1400),ArcadeDifficulty.Normal,
            spawnTimer=1000.0,pending=listOf(PendingThreat(threat,1.2)))
        val next=advanceArcade(run,.1,Random(17))
        assertTrue(next.pending.isEmpty());assertEquals(1,next.bodies.count { it.id==999L })
        assertTrue(next.bodies.first { it.id==999L }.position.x < threat.position.x)
        val again=advanceArcade(next,.1,Random(17));assertEquals(1,again.bodies.count { it.id==999L })
    }
    @Test fun visibleArrivalPortalExpiresWhileItsThreatKeepsFlying() {
        val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
        var run=ArcadeSession(listOf(core),SpaceCamera(core.position,.08f),IntSize(900,1400),ArcadeDifficulty.Normal,spawnTimer=0.0)
        val view=ThreatView(IntSize(1080,2340));run=advanceArcade(run,.01,Random(17),view=view)
        val portal=run.arrivalPortals.single();val id=run.bodies.single { it.kind==BodyKind.Meteor }.id
        repeat(60) { run=advanceArcade(run.copy(spawnTimer=1000.0),1.0/60,Random(17),view=view) }
        assertTrue(run.arrivalPortals.isEmpty());assertTrue(run.bodies.any { it.id==id })
        assertNotEquals(portal.position,run.bodies.first { it.id==id }.position)
    }

}
