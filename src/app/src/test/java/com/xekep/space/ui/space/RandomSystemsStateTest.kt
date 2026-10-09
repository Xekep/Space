package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RandomSystemsStateTest {
    @Test fun generationReplacesTheWorldAndUndoRestoresBodiesNameAndSettings() {
        for (resolved in listOf(false,true)) {
        val game=SpaceGameState(random=Random(17)).apply {
            resize(androidx.compose.ui.unit.IntSize(1080,2340))
            startSandbox(SandboxPresetKind.BinaryStars,"Original")
            toggleSandboxPause(); setCollisions(true); setCollisionMode(SandboxCollisionMode.Debris)
        }
        game.selectBody(game.bodies.first().id); game.prepareOrbit()
        val before=game.sandbox!!
        game.generateRandomSystems("Random",resolved)
        assertEquals(if (resolved) 1000 else 500,game.bodies.size); assertEquals("Random",game.sandbox!!.name)
        assertTrue(game.sandbox!!.paused); assertTrue(game.sandbox!!.collisionsEnabled)
        assertEquals(SandboxCollisionMode.Debris,game.sandbox!!.collisionMode)
        assertNull(game.orbitSourceId); assertTrue(game.dirty)
        assertTrue(game.bodies.map { it.id }.intersect(before.bodies.map { it.id }.toSet()).isEmpty())
        game.undo(); assertEquals(before,game.sandbox)
        }
    }
    @Test fun collisionModeIsPartOfSnapshotsAndCanBeUndone() {
        val game=SpaceGameState().apply { startSandbox(); setCollisions(true) }
        assertEquals(SandboxCollisionMode.Merge,game.snapshot(0)!!.collisionMode)
        game.setCollisionMode(SandboxCollisionMode.Debris)
        val snapshot=game.snapshot(0)!!
        game.undo(); assertEquals(SandboxCollisionMode.Merge,game.sandbox!!.collisionMode)
        game.loadSandbox(snapshot); assertEquals(SandboxCollisionMode.Debris,game.sandbox!!.collisionMode)
        assertTrue(game.sandbox!!.collisionsEnabled)
    }
}
