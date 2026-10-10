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
    @Test fun threatsUseTheSameWorldApproachForZoomedPannedAndRotatedViews() {
        var reference: CelestialBody?=null
        for (zoom in listOf(.15f,1f,6f)) for (rotation in listOf(0.0,1.2)) {
            val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
            val run=ArcadeSession(listOf(core),SpaceCamera(Vec2(600.0,-200.0),zoom),IntSize(900,1400),ArcadeDifficulty.Normal,spawnTimer=0.0)
            val view=ThreatView(IntSize(1080,2340),rotation)
            val announced=advanceArcade(run,.01,Random(2),view=view)
            val meteor=announced.pending.single().body
            reference?.let { assertEquals(it.position,meteor.position); assertEquals(it.velocity,meteor.velocity) }
            reference=meteor
            assertTrue((meteor.position-core.position).magnitude()>700)
            val entered=advanceArcade(announced.copy(pending=listOf(announced.pending.single().copy(seconds=0.0))),.01,Random(2),view=view)
            val flying=advanceArcade(entered,.1,Random(2),view=view).bodies.first { it.id == meteor.id }
            assertTrue((flying.position-core.position).magnitude()<(meteor.position-core.position).magnitude())
        }
    }
}
