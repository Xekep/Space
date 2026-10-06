package com.xekep.space.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
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
}
