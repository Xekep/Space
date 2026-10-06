package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Repeatable probes, not a substitute for observing human players. */
class GameplayAnalysisTest {
    @Test fun characterizeArcadeTimeLossWhenFramesAreSlow() {
        fun elapsed(frameSeconds: Double, frames: Int): Double {
            val game = SpaceGameState(random = Random(0)).apply { resize(IntSize(1080, 1920)); startArcade() }
            repeat(frames) { game.update(frameSeconds) }
            return game.arcade!!.elapsed
        }
        val atTwentyFps = elapsed(0.05, 200)
        val atTenFps = elapsed(0.1, 100)
        println("FRAME_TIME,wallSeconds=10,20fps=$atTwentyFps,10fps=$atTenFps")
        assertEquals(10.0, atTwentyFps, 1e-6)
        assertEquals(10.0, atTenFps, 1e-6)
    }

    @Test fun compareIdleAndSimplePoliciesAcrossDifficultiesAndArenaSizes() {
        println("BALANCE,policy,difficulty,width,height,seed,seconds,score,destroyed,lives,maxBodies")
        for (size in listOf(IntSize(720, 1280), IntSize(1080, 1920))) {
            for (difficulty in ArcadeDifficulty.entries) {
                for (policy in listOf("idle", "ring", "feed-core")) {
                    repeat(6) { seed ->
                        val game = SpaceGameState(random = Random(seed)).apply { resize(size); startArcade(difficulty) }
                        var tick = 0
                        var maxBodies = game.bodies.size
                        while (game.arcade!!.lives > 0 && tick < 2400) {
                            if (policy == "ring" && tick % 20 == 0) {
                                val core = game.bodies.first { it.kind == BodyKind.Core }
                                val angle = (tick / 20 % 8) * Math.PI / 4
                                val radius = game.arcade!!.arena.width * 0.22
                                val offset = Vec2(cos(angle), sin(angle)) * radius
                                val start = core.position + offset
                                game.launch(TouchPreview(start, start + offset.perpendicular().normalized() * 120.0, 0), 0.5)
                            }
                            if (policy == "feed-core" && tick > 0 && tick % 80 == 0) {
                                val center = game.bodies.first { it.kind == BodyKind.Core }.position
                                game.launch(TouchPreview(center, center, 0), 4.0)
                            }
                            game.update(0.05)
                            maxBodies = maxOf(maxBodies, game.bodies.size)
                            if (tick % 100 == 0) assertFinite(game.bodies)
                            tick++
                        }
                        val run = game.arcade!!
                        println("BALANCE,$policy,${difficulty.name},${size.width},${size.height},$seed,${run.elapsed},${run.score},${run.destroyed},${run.lives},$maxBodies")
                        assertFinite(game.bodies)
                        assertTrue(run.score.isFinite())
                    }
                }
            }
        }
    }

    @Test fun sandboxPresetsRemainFiniteAtEveryTimeScale() {
        for (preset in listOf(SandboxPresetKind.SolarSystem, SandboxPresetKind.BinaryStars, SandboxPresetKind.ClassicOrbits)) {
            for (speed in listOf(0.25, 1.0, 3.0, 6.0)) {
                val game = SpaceGameState().apply { resize(IntSize(1080, 1920)); startSandbox(preset); setTimeScale(speed) }
                val initialEnergy = SimulationEngine.totalEnergy(game.bodies)
                repeat(1200) { game.update(0.05) }
                assertFinite(game.bodies)
                assertEquals(if (preset == SandboxPresetKind.SolarSystem) com.xekep.space.sim.SolarBody.entries.size else if (preset == SandboxPresetKind.ClassicOrbits) 9 else 2, game.bodies.size)
                val drift = abs(SimulationEngine.totalEnergy(game.bodies) - initialEnergy) / abs(initialEnergy)
                println("SANDBOX,$preset,$speed,${60 * speed},${game.bodies.size},$drift")
                assertTrue("Energy drift for $preset at $speed", drift < 0.02)
            }
        }
    }

    @Test fun characterizeMeteorAndPlayerMerge() {
        val meteor = CelestialBody(100001, Vec2.Zero, Vec2.Zero, 200.0, 20f, Color.Red, BodyKind.Meteor)
        val player = CelestialBody(100002, Vec2.Zero, Vec2.Zero, 70.0, 10f, Color.Cyan, BodyKind.Player)
        val result = SimulationEngine.stepArcade(listOf(meteor, player), 0.01)
        assertEquals(1, result.collisions.size)
        assertTrue(result.bodies.none { it.kind == BodyKind.Meteor })
        assertEquals(meteor.id, result.collisions.single().meteorId)
    }

    @Test fun characterizeDragSensitivityAtDifferentCameraScales() {
        val values = listOf(0.25f, 1f, 4f).map { zoom ->
            val game = SpaceGameState().apply { resize(IntSize(1080, 1920)); startSandbox(SandboxPresetKind.Empty) }
            game.transformCamera(Offset(540f, 960f), Offset.Zero, zoom)
            game.launch(game.previewAt(Offset(500f, 900f), Offset(580f, 900f), 0), 0.0)
            val speed = game.bodies.single().velocity.magnitude()
            println("DRAG,$zoom,80,$speed")
            speed
        }
        assertEquals(values[0], values[1], 1e-6)
        assertEquals(values[1], values[2], 1e-6)
    }

    @Test fun combatPoliciesStayBoundedAcrossDifficultiesAndSeeds() {
        println("COMBAT,policy,difficulty,seed,seconds,score,intercepts,maxShots,maxCraft")
        var intercepts = 0
        for (difficulty in ArcadeDifficulty.entries) for (kind in listOf(BodyKind.Ship, BodyKind.Rocket)) repeat(6) { seed ->
            val game = SpaceGameState(random = Random(seed)).apply {
                resize(IntSize(1080, 1920)); startArcade(difficulty); chooseSpawnKind(kind)
            }
            var tick = 0; var maxShots = 0; var maxCraft = 0
            while (game.arcade!!.lives > 0 && tick < 4800) {
                if (tick % 40 == 0) {
                    val core = game.bodies.first { it.kind == BodyKind.Core }
                    val angle = tick / 40 * Math.PI / 3
                    val radius = Vec2(cos(angle), sin(angle)) * 350.0
                    val point = core.position + radius
                    game.launch(TouchPreview(point, point + radius.perpendicular().normalized() * 100.0, 0), .1)
                }
                game.update(.05)
                val run = game.arcade!!
                maxShots = maxOf(maxShots, run.combat.projectiles.size)
                maxCraft = maxOf(maxCraft, game.bodies.count { it.kind == kind })
                assertTrue(run.combat.projectiles.size <= 48)
                assertTrue(run.combat.projectiles.all { it.position.x.isFinite() && it.position.y.isFinite() && it.remaining <= 1.3 })
                assertTrue(maxCraft <= if (kind == BodyKind.Ship) 3 else 10)
                tick++
            }
            val run = game.arcade!!
            intercepts += run.destroyed
            assertFinite(game.bodies)
            println("COMBAT,$kind,$difficulty,$seed,${run.elapsed},${run.score},${run.destroyed},$maxShots,$maxCraft")
        }
        assertTrue("Automatic weapons must intercept enemies in actual runs", intercepts > 30)
    }

    private fun assertFinite(bodies: List<CelestialBody>) {
        bodies.forEach {
            assertTrue(it.position.x.isFinite() && it.position.y.isFinite())
            assertTrue(it.velocity.x.isFinite() && it.velocity.y.isFinite())
            assertTrue(it.mass > 0 && it.mass.isFinite())
        }
    }
}
