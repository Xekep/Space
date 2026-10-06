package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PhysicsPerformanceRegressionTest {
    private fun scene(count: Int) = List(count) { i ->
        CelestialBody(i.toLong() + 1, Vec2((i % 10) * 150.0, (i / 10) * 150.0),
            Vec2((i % 3 - 1) * 2.0, (i % 5 - 2) * 2.0), 70.0, 8f, Color.Cyan)
    }

    @Test fun adaptiveStepsMatchThePreviousRk4CalculationAtSixtyAndHundredBodies() {
        for (count in listOf(60, 100)) for (speed in listOf(1.0, 6.0)) {
            var adaptive = scene(count); var reference = adaptive
            val energy = SimulationEngine.totalEnergy(adaptive)
            repeat(120) {
                adaptive = NumericIntegrator.advance(adaptive, speed / 60.0, SimulationEngine.sandboxStepLimit(adaptive))
                reference = NumericIntegrator.advance(reference, speed / 60.0, 1.0 / 240.0)
            }
            val positionError = adaptive.indices.maxOf { (adaptive[it].position - reference[it].position).magnitude() }
            val velocityError = adaptive.indices.maxOf { (adaptive[it].velocity - reference[it].velocity).magnitude() }
            println("ADAPTIVE,count=$count,speed=$speed,positionError=$positionError,velocityError=$velocityError")
            assertTrue("Position differs by $positionError", positionError < 0.1)
            assertTrue("Velocity differs by $velocityError", velocityError < 0.1)
            assertTrue(abs(SimulationEngine.totalEnergy(adaptive) - energy) / abs(energy) < 0.002)
        }
    }

    @Test fun closeHeavyBodiesRetainTheOriginalSmallStep() {
        val pair = scene(2).map { it.copy(position = Vec2.Zero, mass = 12000.0) }
        assertEquals(1.0 / 240.0, SimulationEngine.sandboxStepLimit(pair), 0.0)
        assertEquals(1.0 / 30.0, SimulationEngine.sandboxStepLimit(scene(60)), 0.0)
    }

    @Test fun collisionSubstepsRecordOneTrailSamplePerSimulationTick() {
        val initial = scene(60)
        val next = SimulationEngine.stepSandbox(initial, 0.1, SimulationEngine.totalEnergy(initial), true).bodies
        assertEquals(60, next.size)
        next.forEach { assertEquals(2, it.trail.size); assertEquals(it.position, it.trail.last()) }
    }
}
