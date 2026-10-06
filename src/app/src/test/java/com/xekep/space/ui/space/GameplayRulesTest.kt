package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameplayRulesTest {
    private fun game() = SpaceGameState(random = Random(17)).apply { resize(IntSize(1080, 1920)) }
    @Test fun frameRateAndDisplaySizeDoNotChangeArcadeRules() {
        val sessions = listOf(10, 30, 60, 120).map { fps ->
            val game = game().apply { resize(IntSize(fps * 20 + 480, fps * 30 + 800)); startArcade() }
            repeat(fps * 20) { game.update(1.0 / fps) }
            game.arcade!!
        }
        sessions.drop(1).forEach { next ->
            val first = sessions.first()
            assertEquals(first.elapsed, next.elapsed, 1e-8)
            assertEquals(first.score, next.score, 1e-8)
            assertEquals(first.lives, next.lives)
            assertEquals(first.bodies.map { it.position }, next.bodies.map { it.position })
        }
    }
    @Test fun interceptionRemovesThreatAndRewardsItOnlyOnce() {
        val core = SimulationEngine.arcadeBodies(Vec2(900.0, 1400.0)).first()
        val meteor = CelestialBody(99991, Vec2(700.0, 700.0), Vec2.Zero, 200.0, 20f, Color.Red, BodyKind.Meteor)
        val defender = meteor.copy(id = 99992, mass = 600.0, kind = BodyKind.Player)
        val run = ArcadeSession(listOf(core, meteor, defender), SpaceCamera(core.position), IntSize(900, 1400), ArcadeDifficulty.Normal, energy = 50.0)
        val next = advanceArcade(run, 1.0 / 60, Random(0))
        assertEquals(1, next.destroyed); assertEquals(100.0, next.score, 1e-6)
        assertTrue(next.bodies.none { it.id == meteor.id })
        val again = advanceArcade(next, 1.0 / 60, Random(0))
        assertEquals(next.destroyed, again.destroyed); assertEquals(next.score, again.score, 0.0)
    }
    @Test fun arenaCleanupRemovesDepartedMeteorsButKeepsRemoteDefenders() {
        val core = SimulationEngine.arcadeBodies(Vec2(900.0, 1400.0)).first()
        val defender = CelestialBody(99981, Vec2(-5000.0, 700.0), Vec2.Zero, 70.0, 8f, Color.Cyan, BodyKind.Player)
        val meteor = defender.copy(id = 99982, position = Vec2(5000.0, 700.0), kind = BodyKind.Meteor)
        val run = ArcadeSession(listOf(core, defender, meteor), SpaceCamera(defender.position), IntSize(900, 1400), ArcadeDifficulty.Normal)
        val next = advanceArcade(run, 1.0 / 60, Random(0))
        assertEquals(setOf(core.id, defender.id), next.bodies.map { it.id }.toSet())
        assertEquals(0, next.destroyed)
        assertEquals(0.0, next.score, 0.0)
    }
    @Test fun simultaneousCoreHitsCostOneLifeAndLeaveTheCoreUnchanged() {
        val core = SimulationEngine.arcadeBodies(Vec2(900.0, 1400.0)).first()
        val meteors = List(4) { core.copy(id = 99900L + it, mass = 100.0, kind = BodyKind.Meteor) }
        val run = ArcadeSession(listOf(core) + meteors, SpaceCamera(core.position), IntSize(900, 1400), ArcadeDifficulty.Normal)
        val next = advanceArcade(run, 1.0 / 60, Random(0))
        assertEquals(3, next.lives); assertEquals(0.0, next.score, 0.0); assertEquals(0, next.destroyed)
        assertEquals(core.position, next.bodies.single().position); assertEquals(core.mass, next.bodies.single().mass, 0.0)
    }
    @Test fun pendingWarningsPrecedeMeteorEntryAndRestingStopsSpawns() {
        val game = game().apply { startArcade(ArcadeDifficulty.Easy) }
        repeat(181) { game.update(1.0 / 60) }
        assertTrue(game.arcade!!.pending.isNotEmpty()); assertTrue(game.bodies.none { it.kind == BodyKind.Meteor })
        repeat(100) { game.update(1.0 / 60) }
        assertTrue(game.bodies.any { it.kind == BodyKind.Meteor })
        val run = game.arcade!!.copy(elapsed = 24.5, pending = emptyList(), spawnTimer = 0.0)
        assertTrue(advanceArcade(run, 0.01, Random(0)).pending.isEmpty())
    }
    @Test fun launchMassIsLimitedByEnergyAndFailuresExplainWhy() {
        val game = game().apply { startArcade() }
        val preview = TouchPreview(Vec2(100.0, 100.0), Vec2(100.0, 100.0), 0)
        game.launch(preview, 4.0)
        val before = game.bodies.size
        game.launch(preview, 4.0)
        assertEquals(before, game.bodies.size); assertNotNull(game.feedback)
        assertTrue(game.arcade!!.energy >= 0)
    }
    @Test fun editingDeletingAndUndoRestoreTheCompleteScene() {
        val game = game().apply { startSandbox(SandboxPresetKind.BinaryStars); toggleSandboxPause() }
        val before = game.sandbox!!
        game.selectBody(game.bodies.first().id)
        game.editSelected(2500.0, Vec2(3.0, 7.0))
        assertEquals(2500.0, game.bodies.first().mass, 0.0)
        game.undo(); assertEquals(before, game.sandbox)
        game.selectBody(game.bodies.first().id); game.deleteSelected(); assertEquals(1, game.bodies.size)
        game.undo(); assertEquals(before, game.sandbox)
    }
    @Test fun orbitHelperGivesTangentialVelocityAndCheckpointCanBeRestored() {
        val game = game().apply { startSandbox(SandboxPresetKind.BinaryStars); toggleSandboxPause(); saveCheckpoint() }
        val before = game.sandbox!!
        val parent = game.bodies.first()
        game.selectBody(parent.id); game.prepareOrbit()
        val point = parent.position + Vec2(-300.0, 0.0)
        game.launch(TouchPreview(point, point, 0), 0.0)
        val satellite = game.bodies.last()
        assertTrue(satellite.mass <= parent.mass * 0.02)
        assertEquals(0.0, satellite.velocity.x - parent.velocity.x, 1e-6)
        assertTrue(satellite.velocity.y < parent.velocity.y)
        game.restoreCheckpoint(); assertEquals(before, game.sandbox)
    }
    @Test fun inputSensitivityIsIndependentOfScreenDensity() {
        val velocities = listOf(1f, 2f, 3f).map { density ->
            val game = game().apply { startSandbox(SandboxPresetKind.Empty); updateDensity(density) }
            game.launch(game.previewAt(Offset(300f, 300f), Offset(300f + 60f * density, 300f), 0), 0.0)
            game.bodies.single().velocity
        }
        assertEquals(velocities[0], velocities[1]); assertEquals(velocities[1], velocities[2])
    }
    @Test fun longStallsPauseAndSmallBacklogsAreRetained() {
        val game = game().apply { startArcade() }
        game.update(0.5); assertTrue(game.behind)
        game.update(0.05); game.update(0.05)
        assertEquals(0.6, game.arcade!!.elapsed, 1e-6)
        game.update(3.0); assertTrue(game.menuOpen); assertNotNull(game.feedback)
    }
    @Test fun practiceProtectsLivesAndDoesNotRaiseTheOfficialRecord() {
        val game = game().apply { beginTutorial(AppMode.Arcade) }
        repeat(3600) { game.update(1.0 / 60) }
        assertEquals(6, game.arcade!!.lives)
        assertEquals(0.0, game.recordFor(ArcadeDifficulty.Easy), 0.0)
        game.skipTutorial()
        assertFalse(game.arcade!!.practice); assertEquals(0.0, game.arcade!!.elapsed, 0.0)
    }
}
