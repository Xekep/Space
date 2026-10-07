package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import com.xekep.space.storage.SandboxSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SandboxPacingTest {
    @Test fun largeSceneAccumulatesTwoDisplayFramesIntoOneFixedPhysicsTick() = runBlocking {
        var nextId=1L
        val bodies=RandomSystems.create(kotlin.random.Random(17)) { nextId++ }
        val energy=SimulationEngine.totalEnergy(bodies)
        val game=SpaceGameState().apply { loadSandbox(SandboxSnapshot(bodies,Vec2.Zero,.03f,energy,0)) }
        game.updateSandboxAsync(1.0/60,budgeted=true)
        assertSame(bodies,game.bodies)
        game.updateSandboxAsync(1.0/60,budgeted=true)
        assertEquals(SimulationEngine.stepSandbox(bodies,1.0/30,energy).bodies,game.bodies)
        assertFalse(game.menuOpen); assertFalse(game.sandbox!!.paused)
    }
    @Test fun budgetedWorkerCapsCatchupAndKeepsTheSceneResponsiveUnderLongFrameDebt() = runBlocking {
        val bodies=listOf(CelestialBody(1,Vec2(-1000.0,0.0),Vec2(100.0,0.0),70.0,8f,Color.Cyan),
            CelestialBody(2,Vec2(1000.0,0.0),Vec2(-100.0,0.0),70.0,8f,Color.Cyan))
        val snapshot=SandboxSnapshot(bodies,Vec2.Zero,1f,SimulationEngine.totalEnergy(bodies),0)
        val paced=SpaceGameState().apply { loadSandbox(snapshot) }
        val reference=SpaceGameState().apply { loadSandbox(snapshot); update(2.0/60) }
        paced.updateSandboxAsync(3.0,budgeted=true)
        assertFalse(paced.menuOpen); assertFalse(paced.sandbox!!.paused)
        assertEquals(reference.bodies,paced.bodies)
        paced.openMenu(); val paused=paced.bodies
        paced.updateSandboxAsync(.5,budgeted=true)
        assertSame(paused,paced.bodies)
    }
}
