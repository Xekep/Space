package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArcadeCombatTest {
    private fun body(id: Long, kind: BodyKind, x: Double, y: Double = 700.0, mass: Double = 100.0) =
        CelestialBody(id, Vec2(x, y), Vec2.Zero, mass, 8f, Color.Cyan, kind)

    @Test fun cyclingAndEnergyCostsApplyToAllThreeArcadeLaunches() {
        val game = SpaceGameState().apply { resize(IntSize(1080, 1920)); startArcade() }
        listOf(BodyKind.Player, BodyKind.Ship, BodyKind.Rocket).forEachIndexed { i, kind ->
            val before = game.arcade!!.energy
            val point = Vec2(100.0 + i * 200, 100.0)
            game.launch(TouchPreview(point, point, 0), 0.0)
            assertEquals(kind, game.bodies.last().kind)
            assertEquals(before - launchCost(game.bodies.last()), game.arcade!!.energy, 1e-7)
            game.cycleSpawnKind()
        }
        assertEquals(BodyKind.Ambient, game.spawnKind)
        game.openMenu(); val count = game.bodies.size
        game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.0)
        assertEquals(count, game.bodies.size)
    }

    @Test fun shipSteersAndFiresTowardTheNearestEnemy() {
        val ship = body(1, BodyKind.Ship, 700.0)
        val meteor = body(2, BodyKind.Meteor, 900.0)
        val prepared = prepareCombat(listOf(ship, meteor), ArcadeCombat(craft = mapOf(1L to CraftStatus(cooldown = 0.0))), .1)
        assertTrue(prepared.bodies.first().velocity.x > 0.0)
        assertTrue(prepared.bodies.first().heading.x in .3.. .4)
        assertEquals(1.0, prepared.bodies.first().heading.magnitude(), 1e-8)
        assertEquals(1, prepared.combat.projectiles.size)
        assertTrue(prepared.combat.projectiles.single().velocity.x > 600)
        val cooling = prepareCombat(prepared.bodies, prepared.combat, .1)
        assertEquals(1, cooling.combat.projectiles.size)
    }

    @Test fun projectileCrossingKillsExactlyOneEnemyAndCreditsItsShipOnce() {
        val core = body(10, BodyKind.Core, 450.0)
        val enemy = body(20, BodyKind.Meteor, 720.0)
        val shot = SpaceProjectile(Vec2(700.0, 700.0), Vec2(5000.0, 0.0), 40)
        val run = ArcadeSession(listOf(core, enemy), SpaceCamera(core.position), IntSize(900, 1400), ArcadeDifficulty.Normal,
            combat = ArcadeCombat(projectiles = listOf(shot)))
        val next = advanceArcade(run, 1.0 / 60, Random(0))
        assertEquals(1, next.destroyed); assertEquals(100.0, next.score, 1e-7)
        assertTrue(40L in next.successfulLaunches)
        assertTrue(next.combat.projectiles.isEmpty()); assertEquals(1, next.explosions.size)
        val again = advanceArcade(next, 1.0 / 60, Random(0))
        assertEquals(next.score, again.score, 0.0); assertEquals(1, again.destroyed)
    }

    @Test fun heavyEnemiesNeedSeveralShotsAndProjectilesExpire() {
        val enemy = body(1, BodyKind.Meteor, 10.0, 0.0, 550.0)
        val shot = SpaceProjectile(Vec2.Zero, Vec2(1000.0, 0.0), 2)
        var targets = listOf(enemy)
        repeat(2) {
            val impact = advanceProjectiles(targets, ArcadeCombat(listOf(shot)), .02)
            targets = impact.bodies; assertTrue(impact.events.isEmpty())
        }
        assertEquals(310.0, targets.single().mass, 1e-7)
        repeat(2) { targets = advanceProjectiles(targets, ArcadeCombat(listOf(shot)), .02).bodies }
        val kill = advanceProjectiles(targets, ArcadeCombat(listOf(shot)), .02)
        assertTrue(kill.bodies.isEmpty()); assertEquals(1, kill.events.size)
        assertTrue(advanceProjectiles(emptyList(), ArcadeCombat(listOf(shot.copy(remaining = .01))), .02).combat.projectiles.isEmpty())
    }

    @Test fun vehiclesExplodeOnCelestialTargetsAndFastRocketInterceptsCount() {
        val planet = body(1, BodyKind.Ambient, 0.0, 0.0).copy(mass = 1e-8, radius = .01f)
        val rocket = body(2, BodyKind.Rocket, -10.0, 0.0).copy(mass = 1e-8, radius = .01f, velocity = Vec2(5000.0, 0.0))
        val hit = SimulationEngine.stepSandbox(listOf(planet, rocket), .01, 0.0, false)
        assertEquals(listOf(planet.id), hit.bodies.map { it.id }); assertEquals(1, hit.collisions.size)
        assertTrue(hit.collisions.single().vehicleExplosion)
        val intercept = SimulationEngine.stepArcade(listOf(planet.copy(kind = BodyKind.Meteor), rocket), .01)
        assertTrue(intercept.bodies.isEmpty()); assertEquals(planet.id, intercept.collisions.single().meteorId)
        assertEquals(rocket.id, intercept.collisions.single().defenderId)
    }

    @Test fun debrisIsBoundedFadesAndNeverAddsGravitatingBodies() {
        val event = CollisionEvent(BodyKind.Rocket, BodyKind.Core, Vec2.Zero, vehicleExplosion = true)
        val effects = advanceExplosions(emptyList(), List(100) { event }, .01)
        assertEquals(Explosion.MAX_BURSTS, effects.size)
        assertTrue(effects.all { it.particles.size == 24 })
        assertTrue(effects.first().particles.map { it.velocity }.distinct().size > 20)
        assertTrue(advanceExplosions(effects, emptyList(), 1.21).isEmpty())
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty); chooseSpawnKind(BodyKind.Rocket) }
        repeat(2) { game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.0) }
        game.update(1.0 / 60)
        assertTrue(game.bodies.isEmpty()); assertEquals(1, game.explosions.size)
        repeat(80) { game.update(1.0 / 60) }
        assertTrue(game.explosions.isEmpty())
    }

    @Test fun laterWavesSpawnLargerGroupsWithHeavyEnemiesAndPreserveRest() {
        val core = body(1, BodyKind.Core, 450.0)
        fun wave(number: Int): ArcadeSession {
            val run = ArcadeSession(listOf(core), SpaceCamera(), IntSize(900, 1400), ArcadeDifficulty.Normal,
                elapsed = (number - 1) * 28.0 + 3.0, spawnTimer = 0.0)
            return advanceArcade(run, .01, Random(1))
        }
        val first = wave(1); val later = wave(9)
        assertEquals(1, first.pending.size); assertEquals(4, later.pending.size)
        assertTrue(later.pending.any { it.body.mass > 800 })
        assertTrue(later.spawnTimer < first.spawnTimer)
        val resting = later.copy(elapsed = 24.5, pending = emptyList(), spawnTimer = 0.0)
        assertTrue(advanceArcade(resting, .01, Random(1)).pending.isEmpty())
    }

    @Test fun craftAndShotsCannotAccumulateForever() {
        val ship = body(1, BodyKind.Ship, 100.0)
        val expired = prepareCombat(listOf(ship), ArcadeCombat(craft = mapOf(1L to CraftStatus(17.99))), .02)
        assertTrue(expired.bodies.isEmpty()); assertEquals(1, expired.events.size)
        val noEnemies = prepareCombat(listOf(ship), ArcadeCombat(), .1)
        assertTrue(noEnemies.combat.projectiles.isEmpty())
    }

    @Test fun explosionRebasesEnergyBeforeReturningToCelestialOnlyPhysics() {
        val game = SpaceGameState().apply { startSandbox(SandboxPresetKind.Empty) }
        game.launch(TouchPreview(Vec2(2000.0, 0.0), Vec2(2100.0, 0.0), 0), 0.0)
        game.chooseSpawnKind(BodyKind.Rocket)
        repeat(2) { game.launch(TouchPreview(Vec2.Zero, Vec2.Zero, 0), 0.0) }
        game.update(1.0 / 60)
        assertEquals(1, game.bodies.size)
        assertEquals(SimulationEngine.totalEnergy(game.bodies), game.sandbox!!.referenceEnergy, 1e-7)
    }
}
