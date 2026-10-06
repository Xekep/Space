package com.xekep.space.ui.space

import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import kotlin.random.Random

internal fun advanceArcade(current: ArcadeSession, dt: Double, random: Random, control: com.xekep.space.sim.ManualFlightControl? = null): ArcadeSession {
    val routed=com.xekep.space.sim.applyFlightControls(current.bodies,control,dt)
    val prepared = prepareCombat(routed, current.combat, dt, control?.bodyId)
    val step = SimulationEngine.stepArcade(prepared.bodies, dt,control?.bodyId)
    val combatStep = advanceProjectiles(com.xekep.space.sim.advanceWaypoints(prepared.bodies,step.bodies), prepared.combat, dt)
    val events = prepared.events + step.collisions + combatStep.events
    // Arena cleanup applies to spent threats. Player bodies belong to world space,
    // so a launch after camera travel must survive outside the initial arena.
    var bodies = combatStep.bodies.filter { it.kind != BodyKind.Meteor ||
        (it.position.x in -CullMargin..(current.arena.width + CullMargin) &&
            it.position.y in -CullMargin..(current.arena.height + CullMargin)) }
    var immunity = (current.immunity - dt).coerceAtLeast(0.0)
    var lives = current.lives; var stopped = 0; var score = current.score
    var combo = if (current.elapsed - current.lastIntercept > 4.0) (current.combo - dt * 0.5).coerceAtLeast(1.0) else current.combo
    var lastIntercept = current.lastIntercept; var hit = false
    val successful = current.successfulLaunches.toMutableSet()
    events.filter { it.meteorId != null }.distinctBy { it.meteorId }.forEach { event ->
        if (event.secondKind == BodyKind.Core) {
            if (immunity <= 0.0) { if (!current.practice) lives = (lives - 1).coerceAtLeast(0); immunity = 0.6; hit = true; combo = 1.0 }
        } else {
            stopped++; score += 100.0 * combo * current.difficulty.scoreFactor
            combo = (combo + 0.25).coerceAtMost(3.0); lastIntercept = current.elapsed
            if (event.secondKind == BodyKind.Player || event.secondKind == BodyKind.Ship || event.secondKind == BodyKind.Rocket) event.defenderId?.let(successful::add)
        }
    }
    val elapsed = current.elapsed + dt; val cycle = elapsed % 28.0
    if (current.elapsed % 28.0 < 24.0 && cycle >= 24.0) score += (150 + lives * 20) * current.difficulty.scoreFactor
    val wave = 1 + (elapsed / 28.0 + 1e-9).toInt()
    val pending = current.pending.map { it.copy(seconds = it.seconds - dt) }.toMutableList()
    if (lives > 0) bodies = bodies + pending.filter { it.seconds <= 0 }.map { it.body }
    pending.removeAll { it.seconds <= 0 }
    var timer = current.spawnTimer - dt
    if (cycle >= 24.0) timer = 1.5
    if (lives > 0 && cycle < 24.0 && timer <= 0.0) {
        repeat(if (current.practice) 1 else (1 + wave / 3).coerceAtMost(4)) {
            if (pending.size + bodies.count { it.kind == BodyKind.Meteor } < (8 + wave).coerceAtMost(18)) {
                if (current.practice && (pending.isNotEmpty() || bodies.any { it.kind == BodyKind.Meteor })) return@repeat
                val side = if (wave == 1) wave % 4 else (wave + it + random.nextInt(2)) % 4
                var meteor = SimulationEngine.spawnMeteor(Vec2(900.0, 1400.0), wave * 0.4, random, Vec2(450.0, 700.0), side)
                if (!current.practice && wave >= 3 && wave % 2 == 1 && it == 0) {
                    val mass = 500.0 + wave * 40.0
                    meteor = meteor.copy(mass = mass, radius = SimulationEngine.radiusForMass(mass), velocity = meteor.velocity * 0.65)
                }
                pending += PendingThreat(if (current.practice) meteor.copy(position = Vec2(964.0, 700.0), velocity = Vec2(-40.0, 0.0), mass = 90.0,
                    radius = SimulationEngine.radiusForMass(90.0)) else meteor.copy(velocity = meteor.velocity * (0.60 + minOf(wave, 16) * 0.035)), current.difficulty.warningSeconds)
            }
        }
        timer += (4.6 - wave * 0.25).coerceAtLeast(1.4) * current.difficulty.spawnDelay
    }
    return current.copy(bodies = bodies, lives = lives, elapsed = elapsed, pending = pending, spawnTimer = timer,
        energy = (current.energy + dt * EnergyRegenPerSecond + stopped * 8.0).coerceAtMost(MaxEnergy), score = score,
        destroyed = current.destroyed + stopped, combo = combo, lastIntercept = lastIntercept, immunity = immunity,
        combat = combatStep.combat, explosions = com.xekep.space.sim.advanceExplosions(current.explosions, events, dt),
        hitFlash = if (hit) 1.0 else (current.hitFlash - dt * 1.8).coerceAtLeast(0.0), successfulLaunches = successful)
}
