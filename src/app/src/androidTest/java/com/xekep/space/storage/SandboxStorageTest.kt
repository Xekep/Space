package com.xekep.space.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.isVehicle
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SandboxStorageTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "sandbox_test_${UUID.randomUUID()}"
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(preferenceName, mode)
    }
    private val storage = SandboxStorage(context)

    @After fun cleanup() { target.deleteSharedPreferences(preferenceName) }

    @Test fun aSavedLoopContinuesTheSameCycleAfterReloadAndRejectsInvalidLoopIndices() {
        val path=com.xekep.space.sim.FlightPath.through(Vec2.Zero,listOf(Vec2(100.0,0.0),Vec2(100.0,100.0)),true)!!
        val sample=path.sample(path.length-5)
        val ship=com.xekep.space.sim.CelestialBody(1,sample.position,sample.direction*100.0,24.0,8f,
            androidx.compose.ui.graphics.Color.Cyan,com.xekep.space.sim.BodyKind.Ship,heading=sample.direction,
            routePath=path,routeDistance=path.length-5,waypoints=path.remainingPoints(0.0),routeSpeed=100.0)
        val scene=SandboxSnapshot(listOf(ship),Vec2.Zero,1f,0.0,123L)
        val restored=storage.decode(storage.encode(scene))
        assertEquals(scene,restored)
        val next=SimulationEngine.stepSandbox(restored.bodies,.1,0.0,false).bodies.single()
        assertTrue((next.position-path.sample(5.0).position).magnitude() < 1e-6)
        assertNotNull(next.routePath); assertTrue(next.routePath!!.isLoop)
        assertEquals(5.0,next.routeDistance,1e-6)
        val raw=org.json.JSONObject(storage.encode(scene))
        raw.getJSONArray("bodies").getJSONObject(0).put("routeLoopStart",2)
        assertThrows(IllegalArgumentException::class.java) { storage.decode(raw.toString()) }
    }

    @Test fun largerVehicleIconsAreEnabledByDefaultAndTheChoicePersists() {
        val options=GameOptions(context)
        assertTrue(options.largeVehicleIcons)
        options.largeVehicleIcons=false; options.save()
        assertFalse(GameOptions(context).largeVehicleIcons)
        options.largeVehicleIcons=true; options.save()
        assertTrue(GameOptions(context).largeVehicleIcons)
    }

    @Test fun splineProgressAndRocketCoastRoundTripWithoutChangingThePath() {
        val points=listOf(Vec2(150.0,0.0),Vec2(150.0,160.0),Vec2(300.0,160.0))
        val path=com.xekep.space.sim.FlightPath.through(Vec2.Zero,points)!!
        val sample=path.sample(200.0)
        val ship=com.xekep.space.sim.CelestialBody(1,sample.position,sample.direction*100.0,24.0,8f,
            androidx.compose.ui.graphics.Color.Cyan,com.xekep.space.sim.BodyKind.Ship,heading=sample.direction,
            waypoints=path.remainingPoints(200.0),routeSpeed=100.0,routePath=path,routeDistance=200.0)
        val rocket=com.xekep.space.sim.CelestialBody(2,Vec2(1000.0,0.0),Vec2(100.0,0.0),12.0,6f,
            androidx.compose.ui.graphics.Color.White,com.xekep.space.sim.BodyKind.Rocket,fuelRemaining=0.0,driftRemaining=3.25)
        val scene=SandboxSnapshot(listOf(ship,rocket),Vec2.Zero,1f,0.0,123L)
        val restored=storage.decode(storage.encode(scene))
        assertEquals(scene,restored)
        val continued=com.xekep.space.sim.SimulationEngine.stepSandbox(restored.bodies,.1,0.0,false).bodies.first()
        assertTrue((continued.position-path.sample(210.0).position).magnitude() < 1e-6)
        val raw=org.json.JSONObject(storage.encode(scene))
        raw.getJSONArray("bodies").getJSONObject(0).put("routeDistance",path.length+1)
        assertThrows(IllegalArgumentException::class.java) { storage.decode(raw.toString()) }
        listOf(-1.0,6.1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                storage.decode(storage.encode(scene.copy(bodies=listOf(rocket.copy(driftRemaining=invalid)))))
            }
        }
    }

    @Test fun fuelReserveRoundTripsAndOlderVehicleSavesReceiveAFullTank() {
        val body=com.xekep.space.sim.CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,8f,androidx.compose.ui.graphics.Color.Cyan,
            com.xekep.space.sim.BodyKind.Ship,fuelRemaining=37.5)
        val scene=SandboxSnapshot(listOf(body),Vec2.Zero,1f,0.0,123L)
        assertEquals(scene,storage.decode(storage.encode(scene)))
        val old=org.json.JSONObject(storage.encode(scene))
        old.getJSONArray("bodies").getJSONObject(0).remove("fuelRemaining")
        assertEquals(180.0,storage.decode(old.toString()).bodies.single().fuelRemaining,0.0)
        listOf(-1.0,181.0).forEach { invalid ->
            assertTrue(runCatching { storage.decode(storage.encode(scene.copy(bodies=listOf(body.copy(fuelRemaining=invalid))))) }.isFailure)
        }
    }

    @Test fun shakeModesMigrateTheOldToggleAndPersistTheirIntensity() {
        val prefs=context.getSharedPreferences("space_options",Context.MODE_PRIVATE)
        prefs.edit().putBoolean("shake",true).commit()
        val migrated=GameOptions(context)
        assertEquals(com.xekep.space.sim.ShakeMode.Inertial,migrated.shakeMode)
        assertEquals(1f,migrated.shakeIntensity,0f)
        migrated.shakeMode=com.xekep.space.sim.ShakeMode.Classic; migrated.shakeIntensity=2.25f; migrated.save()
        assertEquals(com.xekep.space.sim.ShakeMode.Classic,GameOptions(context).shakeMode)
        assertEquals(2.25f,GameOptions(context).shakeIntensity,0f)
        migrated.shakeMode=com.xekep.space.sim.ShakeMode.Off; migrated.save()
        assertFalse(GameOptions(context).shake)
        assertEquals(2.25f,GameOptions(context).shakeIntensity,0f)
    }

    @Test fun starsBlackHolesAndPersistentPilotThrustRoundTripWithValidation() {
        val game=com.xekep.space.ui.space.SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        listOf(com.xekep.space.sim.BodyKind.Star,com.xekep.space.sim.BodyKind.BlackHole,com.xekep.space.sim.BodyKind.Ship).forEachIndexed { i,kind ->
            game.chooseSpawnKind(kind)
            val point=Vec2(i*2000.0,0.0)
            game.launch(com.xekep.space.ui.space.TouchPreview(point,point,0),.5)
        }
        val initial=game.snapshot(123L)!!
        val scene=initial.copy(bodies=initial.bodies.map { if (it.isVehicle) it.copy(pilotThrottle=.5) else it })
        assertEquals(scene,storage.decode(storage.encode(scene)))
        storage.save(1,scene); assertEquals(scene,storage.load(1))
        val invalid=scene.copy(bodies=listOf(scene.bodies.last().copy(pilotThrottle=1.1)))
        assertTrue(runCatching { storage.decode(storage.encode(invalid)) }.isFailure)
    }

    @Test fun tiltControlOptionPersistsAndDefaultsToOff() {
        val options=GameOptions(context)
        assertFalse(options.motionControl)
        options.motionControl=true; options.save()
        assertTrue(GameOptions(context).motionControl)
        options.motionControl=false; options.save()
        assertFalse(GameOptions(context).motionControl)
    }

    @Test fun authoredVehicleRoutesRoundTripAndRejectExcessiveOrInvalidPoints() {
        val body=com.xekep.space.sim.CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,8f,androidx.compose.ui.graphics.Color.Cyan,
            com.xekep.space.sim.BodyKind.Ship,waypoints=listOf(Vec2(100.0,0.0),Vec2(100.0,200.0)),routeSpeed=90.0,routeTolerance=3.0)
        val scene=SandboxSnapshot(listOf(body),Vec2.Zero,1f,0.0,1L)
        assertEquals(scene,storage.decode(storage.encode(scene)))
        val root=org.json.JSONObject(storage.encode(scene))
        val array=org.json.JSONArray()
        repeat(33) { array.put(org.json.JSONObject().put("x",0.0).put("y",0.0)) }
        root.getJSONArray("bodies").getJSONObject(0).put("waypoints",array)
        assertThrows(IllegalArgumentException::class.java) { storage.decode(root.toString()) }
        root.getJSONArray("bodies").getJSONObject(0).put("waypoints",org.json.JSONArray().put(org.json.JSONObject().put("x",1e10).put("y",0.0)))
        assertThrows(IllegalArgumentException::class.java) { storage.decode(root.toString()) }
    }

    @Test fun savedUniverseRestoresCameraAndSimulationControls() {
        val scene = SimulationEngine.sandboxPreset(SandboxPresetKind.BinaryStars)
        val snapshot = SandboxSnapshot(scene.bodies, Vec2(300.0, -20.0), 2f, scene.referenceEnergy, 123L,
            timeScale = 3.0, paused = true, collisionsEnabled = true, preset = SandboxPresetKind.BinaryStars)
        storage.save(1, snapshot)
        assertEquals(snapshot, storage.load(1))
        assertEquals(2, storage.summaries()[0]!!.bodyCount)
    }

    @Test fun savesFromThePreviousVersionStillLoad() {
        context.getSharedPreferences("ignored", Context.MODE_PRIVATE).edit().putString("slot_1",
            """{"bodies":[],"cameraCenter":{"x":42,"y":-3},"zoom":0.5,"referenceEnergy":0,"timestampUtcMillis":123}""").commit()
        val restored = storage.load(1)!!
        assertEquals(Vec2(42.0, -3.0), restored.cameraCenter)
        assertEquals(1.0, restored.timeScale, 0.0)
        assertFalse(restored.paused)
        assertFalse(restored.collisionsEnabled)
    }

    @Test fun namedScenesCanBeExportedAndImported() {
        val scene = SimulationEngine.sandboxPreset(SandboxPresetKind.BinaryStars)
        val snapshot = SandboxSnapshot(scene.bodies, Vec2.Zero, 0.8f, scene.referenceEnergy, 123L,
            preset = SandboxPresetKind.BinaryStars, name = "My binary system")
        assertEquals(snapshot, storage.decode(storage.encode(snapshot)))
        storage.save(2, snapshot)
        assertEquals("My binary system", storage.summaries()[1]!!.name)
    }

    @Test fun importedScenesRejectDuplicateIdsWithoutChangingSavedSlots() {
        val scene = SimulationEngine.sandboxPreset(SandboxPresetKind.BinaryStars)
        val valid = SandboxSnapshot(scene.bodies, Vec2.Zero, 1f, scene.referenceEnergy, 123L)
        storage.save(1, valid)
        val body = scene.bodies.first()
        listOf(
            valid.copy(bodies = listOf(body, body)),
            valid.copy(bodies = listOf(body.copy(id = Long.MAX_VALUE))),
            valid.copy(bodies = listOf(body.copy(position = Vec2(1e300, 0.0)))),
            valid.copy(bodies = listOf(body.copy(velocity = Vec2(5001.0, 0.0)))),
        ).forEach { invalid -> assertTrue(runCatching { storage.decode(storage.encode(invalid)) }.isFailure) }
        assertEquals(valid, storage.load(1))
    }

    @Test fun arcadeRecordsAreSeparatedByDifficultyAndRulesVersion() {
        val progress = ArcadeProgress(context)
        progress.saveBestScore(999999.0)
        assertEquals(0.0, progress.records.getValue(com.xekep.space.ui.space.ArcadeDifficulty.Normal), 0.0)
        progress.saveBestScore(com.xekep.space.ui.space.ArcadeDifficulty.Easy, 100.0)
        progress.saveBestScore(com.xekep.space.ui.space.ArcadeDifficulty.Hard, 300.0)
        progress.saveBestScore(com.xekep.space.ui.space.ArcadeDifficulty.Easy, 10.0)
        assertEquals(100.0, progress.records.getValue(com.xekep.space.ui.space.ArcadeDifficulty.Easy), 0.0)
        assertEquals(300.0, progress.records.getValue(com.xekep.space.ui.space.ArcadeDifficulty.Hard), 0.0)
    }

    @Test fun musicPreferencePersistsIndependentlyOfSoundEffects() {
        val options = GameOptions(context)
        assertTrue(options.music)
        assertFalse(options.shake)
        options.music = false
        options.sound = true
        options.save()
        val restored = GameOptions(context)
        assertFalse(restored.music)
        assertTrue(restored.sound)
        restored.music = true
        restored.shake = true
        restored.sound = false
        restored.save()
        assertTrue(GameOptions(context).music)
        assertTrue(GameOptions(context).shake)
        assertFalse(GameOptions(context).sound)
    }

    @Test fun vehicleFuelAndDirectionRoundTripAndInvalidEnginesAreRejected() {
        val game = com.xekep.space.ui.space.SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        listOf(com.xekep.space.sim.BodyKind.Ship, com.xekep.space.sim.BodyKind.Rocket).forEachIndexed { i, kind ->
            game.chooseSpawnKind(kind)
            game.launch(com.xekep.space.ui.space.TouchPreview(Vec2(i * 1000.0, 0.0), Vec2(i * 1000.0 + 20.0, 10.0), 0), 0.2)
        }
        val snapshot = game.snapshot(123L)!!
        storage.save(3, snapshot)
        assertEquals(snapshot, storage.load(3))
        assertEquals(snapshot, storage.decode(storage.encode(snapshot)))
        val rocket = snapshot.bodies.last()
        listOf(rocket.copy(burnRemaining = 4.0), rocket.copy(heading = Vec2.Zero)).forEach { body ->
            assertTrue(runCatching { storage.decode(storage.encode(snapshot.copy(bodies = listOf(body)))) }.isFailure)
        }
    }
    @Test fun solarBodiesWithSmallPhysicalMassesAndRadiiRoundTrip() {
        val scene = SimulationEngine.sandboxPreset()
        val snapshot = SandboxSnapshot(scene.bodies, Vec2.Zero, .01f, scene.referenceEnergy, 123L)
        assertEquals(snapshot, storage.decode(storage.encode(snapshot)))
        storage.save(1, snapshot)
        assertEquals(snapshot, storage.load(1))
        val sun = snapshot.bodies.first().copy(mass = 100100.0, solar = null)
        val merged = snapshot.copy(bodies = listOf(sun))
        assertEquals(merged, storage.decode(storage.encode(merged)))
    }

    @Test fun newCombatBalanceDoesNotReusePreviousRulesRecords() {
        context.getSharedPreferences("ignored", Context.MODE_PRIVATE).edit().putString("v2_Normal", "9999").commit()
        assertEquals(0.0, ArcadeProgress(context).records.getValue(com.xekep.space.ui.space.ArcadeDifficulty.Normal), 0.0)
    }

    @Test fun debrisAndPilotSpeedRoundTripWithTheRandomSystemsPresetAndOldDefaults() {
        val ship=com.xekep.space.sim.CelestialBody(1,Vec2.Zero,Vec2.Zero,24.0,1f,
            androidx.compose.ui.graphics.Color.Cyan,com.xekep.space.sim.BodyKind.Ship,pilotTargetSpeed=320.0)
        val fragment=com.xekep.space.sim.CelestialBody(2,Vec2(100.0,0.0),Vec2.Zero,1.0,.5f,
            androidx.compose.ui.graphics.Color.Gray,isDebris=true)
        val snapshot=SandboxSnapshot(listOf(ship,fragment),Vec2.Zero,1f,0.0,0,preset=SandboxPresetKind.RandomSystems)
        assertEquals(snapshot,storage.decode(storage.encode(snapshot)))
        val raw=org.json.JSONObject(storage.encode(snapshot))
        raw.getJSONArray("bodies").getJSONObject(0).remove("pilotTargetSpeed")
        raw.getJSONArray("bodies").getJSONObject(1).remove("isDebris")
        val old=storage.decode(raw.toString())
        assertNull(old.bodies[0].pilotTargetSpeed); assertFalse(old.bodies[1].isDebris)
        raw.getJSONArray("bodies").getJSONObject(0).put("pilotTargetSpeed",901.0)
        assertThrows(IllegalArgumentException::class.java) { storage.decode(raw.toString()) }
    }

}
