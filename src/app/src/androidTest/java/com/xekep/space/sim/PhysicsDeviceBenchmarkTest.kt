package com.xekep.space.sim

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physics timing only: the emulator result is not a phone FPS measurement. */
@RunWith(AndroidJUnit4::class)
class PhysicsDeviceBenchmarkTest {
    @Test fun profileSandboxPhysicsAtIncreasingBodyCounts() {
        for (count in listOf(9, 30, 60, 100)) {
            for (speed in listOf(1.0, 6.0)) {
                var bodies = List(count) { index ->
                    val point = Vec2((index % 10) * 150.0 - 675.0, (index / 10) * 150.0 - 675.0)
                    CelestialBody(index.toLong() + 1, point, Vec2.Zero, 70.0, 8f, Color.Cyan)
                }
                val energy = SimulationEngine.totalEnergy(bodies)
                repeat(10) { bodies = SimulationEngine.stepSandbox(bodies, speed / 60.0, energy).bodies }
                val samples = List(40) {
                    val start = System.nanoTime()
                    bodies = SimulationEngine.stepSandbox(bodies, speed / 60.0, energy).bodies
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
                Log.i("SpaceBenchmark", "PHYSICS,count=$count,speed=$speed,medianMs=${samples[20]},p95Ms=${samples[37]}")
                assertTrue(bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
            }
        }
    }
}
