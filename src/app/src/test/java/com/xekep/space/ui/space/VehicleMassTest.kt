package com.xekep.space.ui.space

import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class VehicleMassTest {
    @Test fun holdingCreatesModestLargerMoreExpensiveHullsInBothModes() {
        for (mode in AppMode.entries) for (kind in listOf(BodyKind.Ship, BodyKind.Rocket)) {
            val game=SpaceGameState().apply { resize(IntSize(1080,1920)); if (mode == AppMode.Arcade) startArcade() else startSandbox(SandboxPresetKind.Empty); chooseSpawnKind(kind) }
            val preview=TouchPreview(Vec2(100.0,100.0),Vec2(100.0,100.0),0)
            val light=game.previewBody(preview,0.0)!!; val heavy=game.previewBody(preview,4.0)!!
            assertEquals(1.18,heavy.radius/light.radius.toDouble(),1e-6)
            assertTrue(launchCost(heavy) > launchCost(light))
            assertTrue(bodyScreenRadius(heavy,.15f,3f,true) > bodyScreenRadius(light,.15f,3f,true))
            assertEquals(light.fuelRemaining,heavy.fuelRemaining,0.0)
        }
    }

    @Test fun benefitsAreCappedAndEditingMassPreservesThePhysicalScale() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            assertEquals(0.0,vehicleMassFraction(kind,1e-8),0.0)
            assertEquals(1.0,vehicleMassFraction(kind,1e8),0.0)
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.SolarSystem); chooseSpawnKind(kind) }
            game.launch(TouchPreview(Vec2(5000.0,5000.0),Vec2(5000.0,5000.0),0),0.0)
            val light=game.bodies.last(); game.selectBody(light.id)
            game.editSelected(if (kind == BodyKind.Ship) 96.0 else 36.0,light.velocity)
            assertEquals(light.radius*1.18,game.bodies.last().radius.toDouble(),1e-9)
            assertTrue(game.bodies.last().physicalScale)
        }
    }

    @Test fun heavyHullsUseLessFuelAndTurnSlightlyMoreSlowly() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); chooseSpawnKind(kind) }
            val preview=TouchPreview(Vec2.Zero,Vec2(0.0,-30.0),0)
            val light=game.previewBody(preview,0.0)!!
            val heavy=game.previewBody(preview,4.0)!!.copy(id=2)
            val moved=NumericIntegrator.advance(listOf(light,heavy.copy(position=Vec2(10000.0,0.0))),10.0,1.0/30,alignRockets=false)
            assertEquals(10.0,light.fuelRemaining-moved[0].fuelRemaining,1e-6)
            assertEquals(9.0,heavy.fuelRemaining-moved[1].fuelRemaining,1e-6)
            val a=steerManually(listOf(light),ManualFlightControl(light.id,1.0),.1).single()
            val b=steerManually(listOf(heavy),ManualFlightControl(heavy.id,1.0),.1).single()
            assertTrue(a.heading.x > b.heading.x)
            assertEquals(1.2,heavy.vehicleDamageScale,1e-8)
        }
    }
    @Test fun theHeavyClassBeginsAtTwoSecondsAndHasItsOwnNameAndPrice() {
        val game=SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        val preview=TouchPreview(Vec2.Zero,Vec2.Zero,0)
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) {
            game.chooseSpawnKind(kind)
            val standard=game.previewBody(preview,1.999)!!
            val heavy=game.previewBody(preview,2.0)!!
            assertEquals(VehicleHullClass.Standard,standard.hullClass)
            assertEquals(VehicleHullClass.Heavy,heavy.hullClass)
            assertNotEquals(standard.labelId(),heavy.labelId())
            assertEquals(heavyHullPremium(kind)+(heavy.mass-standard.mass)*.1,launchCost(heavy)-launchCost(standard),1e-8)
            if (kind == BodyKind.Ship) {
                val guardian=heavy.copy(shipClass=ShipClass.Guardian)
                assertEquals(com.xekep.space.R.string.spawn_heavy_guardian,guardian.labelId())
                assertEquals(launchCost(heavy)+10,launchCost(guardian),1e-8)
            }
        }
    }
    @Test fun limitedEnergyNeverLaunchesAnUnaffordableHeavyHull() {
        for (kind in listOf(BodyKind.Ship,BodyKind.Rocket)) for (role in ShipClass.entries) {
            val minimum=if (kind == BodyKind.Ship) 24.0 else 12.0
            val requested=if (kind == BodyKind.Ship) 96.0 else 36.0
            val heavyCost=launchCost(kind,heavyHullMass(kind),role)
            assertNull(affordableVehicleMass(kind,requested,launchCost(kind,minimum,role)-.01,role))
            for (energy in listOf(launchCost(kind,minimum,role),heavyCost-.01,heavyCost,heavyCost+1.1,120.0)) {
                val mass=affordableVehicleMass(kind,requested,energy,role)!!
                assertTrue(mass in minimum..requested)
                assertTrue(launchCost(kind,mass,role) <= energy+1e-8)
                assertEquals(if (energy+1e-8 >= heavyCost) VehicleHullClass.Heavy else VehicleHullClass.Standard,vehicleHullClass(kind,mass))
            }
        }
    }

}
