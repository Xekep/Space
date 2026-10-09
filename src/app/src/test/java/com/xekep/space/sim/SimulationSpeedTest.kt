package com.xekep.space.sim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

class SimulationSpeedTest {
    @Test fun speedButtonCyclesThroughInspectionAndAcceleratedTimeWithoutChangingPause() {
        var scale=sandboxTimeScales.first()
        val visited=mutableListOf<Double>()
        repeat(sandboxTimeScales.size) { visited+=scale; assertFalse(simulationSpeedLabel(scale).startsWith("0×")); scale=nextSandboxTimeScale(scale) }
        assertEquals(sandboxTimeScales,visited); assertEquals(sandboxTimeScales.first(),scale,0.0)
    }
    @Test fun inspectionSpeedsAreExclusiveToSolarAndAllCyclesIncreaseBeforeWrapping() {
        for (preset in SandboxPresetKind.entries) {
            val speeds=sandboxTimeScalesFor(preset)
            assertEquals(speeds.sorted(),speeds)
            assertEquals(if (preset == SandboxPresetKind.SolarSystem) .0001 else .25,speeds.first(),0.0)
            speeds.forEachIndexed { index,speed -> assertEquals(speeds[(index+1)%speeds.size],nextSandboxTimeScale(speed,preset),0.0) }
            val game=com.xekep.space.ui.space.SpaceGameState().apply { startSandbox(preset) }
            game.setTimeScale(.001)
            assertEquals(if (preset == SandboxPresetKind.SolarSystem) .001 else 1.0,game.sandbox!!.timeScale,0.0)
        }
    }
    @Test fun oldOrdinarySaveWithInspectionSpeedLoadsAtQuarterAndSolarRetainsItsScale() {
        val game=com.xekep.space.ui.space.SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        val old=game.snapshot(0)!!.copy(timeScale=.001)
        game.loadSandbox(old); assertEquals(.25,game.sandbox!!.timeScale,0.0)
        game.loadSandbox(old.copy(preset=SandboxPresetKind.SolarSystem)); assertEquals(.001,game.sandbox!!.timeScale,0.0)
    }
    @Test fun slowestScaleResolvesLowEarthOrbitsWithoutChangingTheirPhysics() {
        val radius=(SolarBody.Earth.radiusKm+420)/AU_KM*AU_WORLD
        val period=2*PI*sqrt(radius.pow(3)/(400*SolarBody.Earth.worldMass))
        assertTrue(period/.25 < 1.0/30)
        assertTrue(period/.0001 > 30)
        println("LEO periodSimulationSeconds=$period quarterWallSeconds=${period/.25} inspectionWallSeconds=${period/.0001}")
    }
}
