package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SpaceGameStateTest {
    private fun game() = SpaceGameState(random = Random(73)).apply { resize(IntSize(1080, 1920)) }

    @Test fun switchingModesKeepsBothWorldsAndTheirCameras() {
        val game = game()
        game.startArcade(ArcadeDifficulty.Hard)
        game.update(0.05)
        game.transformCamera(Offset(500f, 600f), Offset(80f, -30f), 1.4f)
        val arcade = game.arcade
        game.openMenu()
        game.startSandbox(SandboxPresetKind.BinaryStars)
        game.toggleSandboxPause()
        game.setTimeScale(3.0)
        val sandbox = game.sandbox
        game.openMenu()
        game.enterMode(AppMode.Arcade)
        assertEquals(arcade, game.arcade)
        game.openMenu()
        game.enterMode(AppMode.Sandbox)
        assertEquals(sandbox, game.sandbox)
    }

    @Test fun cameraTransformKeepsTheWorldPointUnderTheMovingFingers() {
        val game = game()
        game.startSandbox(SandboxPresetKind.Empty)
        val centroid = Offset(310f, 600f)
        val pan = Offset(120f, -80f)
        val anchor = game.worldAt(centroid)
        game.transformCamera(centroid, pan, 2f)
        val result = game.worldAt(centroid + pan)
        assertEquals(anchor.x, result.x, 1e-6)
        assertEquals(anchor.y, result.y, 1e-6)
        assertEquals(2f, game.camera.zoom, 0f)
    }

    @Test fun zoomClampsAtBothLimitsWithoutLosingItsAnchor() {
        val game = game()
        game.startSandbox(SandboxPresetKind.Empty)
        val center = Offset(200f, 500f)
        val anchor = game.worldAt(center)
        game.transformCamera(center, Offset.Zero, 1000f)
        assertEquals(SandboxMaxZoom, game.camera.zoom, 0f)
        game.transformCamera(center, Offset.Zero, 0.000001f)
        assertEquals(SandboxMinZoom, game.camera.zoom, 0f)
        assertEquals(anchor.x, game.worldAt(center).x, 0.001)
        assertEquals(anchor.y, game.worldAt(center).y, 0.001)
    }

    @Test fun pausedSandboxCanBeEditedAndEmptySceneStaysEmpty() {
        val game = game()
        game.startSandbox(SandboxPresetKind.Empty)
        game.update(0.05)
        assertTrue(game.bodies.isEmpty())
        game.toggleSandboxPause()
        game.launch(TouchPreview(Vec2.Zero, Vec2(20.0, 0.0), 0), 0.1)
        val bodies = game.bodies
        game.update(0.05)
        assertEquals(1, bodies.size)
        assertEquals(bodies, game.bodies)
        game.openMenu()
        game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.1)
        assertEquals(bodies, game.bodies)
    }

    @Test fun saveRestoresTimeSettingsAndReservesIdsForNewBodies() {
        val game = game()
        game.startSandbox(SandboxPresetKind.BinaryStars)
        game.setTimeScale(6.0)
        game.setCollisions(true)
        game.toggleSandboxPause()
        val snapshot = game.snapshot(123L)!!
        val restored = game()
        restored.loadSandbox(snapshot)
        assertEquals(game.sandbox, restored.sandbox)
        restored.launch(TouchPreview(Vec2(2000.0, 0.0), Vec2(2000.0, 10.0), 0), 0.0)
        assertEquals(restored.bodies.size, restored.bodies.map { it.id }.distinct().size)
    }

    @Test fun mergeSettingChangesCollisionBehavior() {
        val first = SimulationEngine.createBody(Vec2.Zero, Vec2.Zero, 0.0)
        val second = SimulationEngine.createBody(Vec2.Zero, Vec2.Zero, 0.0)
        val bodies = listOf(first, second)
        val energy = SimulationEngine.totalEnergy(bodies)
        assertEquals(2, SimulationEngine.stepSandbox(bodies, 0.01, energy, false).bodies.size)
        val merged = SimulationEngine.stepSandbox(bodies, 0.01, energy, true).bodies
        assertEquals(1, merged.size)
        assertEquals(first.mass + second.mass, merged.single().mass, 0.0)
    }

    @Test fun navigatingTheCameraDoesNotChangeArcadeSpawnsOrSimulation() {
        val first = game()
        val second = game()
        first.startArcade()
        second.startArcade()
        second.transformCamera(Offset(400f, 500f), Offset(10000f, 5000f), 0.2f)
        repeat(40) { first.update(0.05); second.update(0.05) }
        assertEquals(first.arcade!!.score, second.arcade!!.score, 0.0)
        assertEquals(first.arcade!!.lives, second.arcade!!.lives)
        assertEquals(first.bodies.map { it.position }, second.bodies.map { it.position })
        assertEquals(first.bodies.map { it.velocity }, second.bodies.map { it.velocity })
    }

    @Test fun menuFreezesBothSessionsAndPersistsBestScore() {
        var persisted = 0.0
        val game = SpaceGameState(saveBestScore = { persisted = it }, initialRecords = mapOf(ArcadeDifficulty.Easy to 123.0)).apply { resize(IntSize(1080, 1920)) }
        game.startArcade(ArcadeDifficulty.Easy)
        assertEquals(6, game.arcade!!.lives)
        game.update(0.05)
        val before = game.arcade
        game.openMenu()
        game.update(0.05)
        assertEquals(before, game.arcade)
        assertTrue(persisted > 0.0)
        assertEquals(game.bestScore, persisted, 0.0)
    }

    @Test fun bodiesCreatedAfterCameraTravelRemainOutsideTheInitialArena() {
        for (mode in AppMode.entries) {
            for (zoomChange in listOf(0.3f, 1f, 3f)) {
                for (direction in listOf(Offset(-1f, -1f), Offset(-1f, 1f), Offset(1f, -1f), Offset(1f, 1f))) {
                    val game = game()
                    if (mode == AppMode.Arcade) game.startArcade() else game.startSandbox(SandboxPresetKind.Empty)
                    val screenPoint = Offset(540f, 960f)
                    game.transformCamera(screenPoint, direction * 5000f, 1f)
                    game.transformCamera(screenPoint, Offset.Zero, zoomChange)
                    val expected = game.worldAt(screenPoint)
                    game.finishGesture(game.previewAt(screenPoint, screenPoint, 0), 0.0)
                    val created = game.bodies.last()
                    assertEquals(expected, created.position)
                    repeat(60) { game.update(1.0 / 60.0) }
                    assertTrue("$mode at $expected lost the created body", game.bodies.any { it.id == created.id })
                }
            }
        }
    }
}
