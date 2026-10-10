package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeFairnessTest {
    private fun run()=ArcadeSession(SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)),
        SpaceCamera(Vec2(450.0,700.0)),IntSize(900,1400),ArcadeDifficulty.Normal)

    @Test fun cameraCannotBuyTimeBeforeTheFirstCoreHit() {
        var reference: Double?=null
        for ((zoom,center,viewport,rotation) in listOf(
            View(1f,Vec2(450.0,700.0),IntSize(1080,2340),0.0),
            View(.15f,Vec2(450.0,700.0),IntSize(1080,2340),1.2),
            View(6f,Vec2(5450.0,-3000.0),IntSize(720,1280),-.8))) {
            val random=Random(17)
            var state=run().copy(camera=SpaceCamera(center,zoom))
            repeat(2400) { if (state.lives == state.difficulty.lives)
                state=advanceArcade(state,1.0/60,random,view=ThreatView(viewport,rotation)) }
            assertTrue("No attack reached core",state.lives < state.difficulty.lives)
            assertTrue(state.elapsed < 40.0)
            reference?.let { assertEquals(it,state.elapsed,1e-9) }; reference=state.elapsed
            println("FAIR_APPROACH zoom=$zoom firstHit=${state.elapsed}")
        }
    }
    private data class View(val zoom: Float,val center: Vec2,val viewport: IntSize,val rotation: Double)

    @Test fun waveAndRestAgreeOnBothSidesOfEveryBoundary() {
        for (wave in 1..21) {
            val start=(wave-1)*28.0
            for (noise in listOf(-1e-10,0.0,1e-10)) {
                val phase=arcadeWavePhase(start+noise)
                assertEquals(wave,phase.wave); assertEquals(0.0,phase.seconds,1e-7); assertFalse(phase.resting)
                val rest=arcadeWavePhase(start+24+noise)
                assertEquals(wave,rest.wave); assertEquals(24.0,rest.seconds,1e-7); assertTrue(rest.resting)
            }
            if (wave > 1) { val previous=arcadeWavePhase(start-.001)
                assertEquals(wave-1,previous.wave); assertTrue(previous.resting) }
        }
    }
    @Test fun upgradeIsOfferedAfterWaveThreeCombatRatherThanAtItsStart() {
        for (elapsed in listOf(55.9999999999981,56.0,56.000000000001)) {
            val beginning=run().copy(elapsed=elapsed,spawnTimer=1000.0)
            assertEquals(3,beginning.wave); assertFalse(beginning.resting)
            assertNull(offerUpgrade(beginning,Random(17)).upgradeOffer)
        }
        // Isolate the wave clock from combat: normal play now waits for the final threat.
        var state=run().copy(spawnTimer=1000.0)
        repeat(4801) { state=advanceArcade(state.copy(spawnTimer=1000.0),1.0/60,Random(17)) }
        assertEquals(3,state.upgradeOffer!!.wave)
        assertEquals(80.0,state.elapsed,1e-7)
    }
    @Test fun quietRewardCannotRepeatOrLeakIntoTheNextWave() {
        val before=run().copy(bodies=emptyList(),elapsed=23.99,spawnTimer=1000.0)
        val reward=advanceArcade(before,.02,Random(1))
        assertEquals(230.0,reward.score,0.0)
        var next=reward
        repeat(300) { next=advanceArcade(next,1.0/60,Random(1)) }
        assertEquals(2,next.wave); assertEquals(reward.score,next.score,0.0)
    }
    @Test fun convoyApproachesAlsoIgnoreCameraAndViewport() {
        val original=run(); val target=Vec2(1450.0,700.0)
        val threat=SimulationEngine.spawnMeteor(Vec2(900.0,1400.0),5.0,Random(2),target,1)
        val first=distantThreat(threat,original,null,target)
        val moved=distantThreat(threat,original.copy(camera=SpaceCamera(Vec2(9999.0,9999.0),.15f)),ThreatView(IntSize(720,1280),1.4),target)
        assertEquals(first,moved)
    }
}
