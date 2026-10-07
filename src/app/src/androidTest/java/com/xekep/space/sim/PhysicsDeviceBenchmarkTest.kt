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
    @Test fun profileFiveHundredBodyCollisionModesWithoutRendering() {
        var nextId=1L
        val initial=RandomSystems.create(kotlin.random.Random(17)) { nextId++ }
        SimulationEngine.reserveBodyIds(initial)
        val energy=SimulationEngine.totalEnergy(initial)
        for ((enabled,mode) in listOf(false to SandboxCollisionMode.Merge,true to SandboxCollisionMode.Merge,true to SandboxCollisionMode.Debris)) {
            var bodies=initial
            repeat(10) { bodies=SimulationEngine.stepSandbox(bodies,1.0/30,energy,enabled,collisionMode=mode).bodies }
            var contacts=0
            val samples=List(30) {
                val start=System.nanoTime()
                val step=SimulationEngine.stepSandbox(bodies,1.0/30,energy,enabled,collisionMode=mode)
                bodies=step.bodies; contacts+=step.collisions.size
                (System.nanoTime()-start)/1_000_000.0
            }.sorted()
            Log.i("SpaceBenchmark","COLLISIONS_500,enabled=$enabled,mode=$mode,medianMs=${samples[15]},p95Ms=${samples[28]},bodies=${bodies.size},contacts=$contacts")
            assertTrue(bodies.all { it.position.x.isFinite() && it.velocity.y.isFinite() })
        }
    }
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
