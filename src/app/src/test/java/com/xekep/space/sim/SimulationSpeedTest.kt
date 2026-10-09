package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

class SimulationSpeedTest {
    @Test fun speedButtonCyclesThroughInspectionAndAcceleratedTimeWithoutChangingPause() {
        var scale=1.0
        val visited=mutableListOf<Double>()
        repeat(sandboxTimeScales.size) { visited+=scale; assertFalse(simulationSpeedLabel(scale).startsWith("0×")); scale=nextSandboxTimeScale(scale) }
        assertEquals(sandboxTimeScales,visited); assertEquals(1.0,scale,0.0)
    }
    @Test fun slowestScaleResolvesLowEarthOrbitsWithoutChangingTheirPhysics() {
        val radius=(SolarBody.Earth.radiusKm+420)/AU_KM*AU_WORLD
        val period=2*PI*sqrt(radius.pow(3)/(400*SolarBody.Earth.worldMass))
        assertTrue(period/.25 < 1.0/30)
        assertTrue(period/.0001 > 30)
        println("LEO periodSimulationSeconds=$period quarterWallSeconds=${period/.25} inspectionWallSeconds=${period/.0001}")
    }
}
