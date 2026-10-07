package com.xekep.space.ui.space

import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import kotlin.random.Random

internal fun advanceArcade(current: ArcadeSession, dt: Double, random: Random, control: com.xekep.space.sim.ManualFlightControl? = null, view: ThreatView? = null): ArcadeSession {
    if (current.upgradeOffer != null || current.lives <= 0) return current
    val routed=com.xekep.space.sim.applyFlightControls(current.bodies,control,dt)
    val prepared = prepareCombat(routed, current.combat, dt, control?.bodyId,current.gunIntervalScale)
    val step = SimulationEngine.stepArcade(prepared.bodies, dt,control?.bodyId)
    val combatStep = advanceProjectiles(com.xekep.space.sim.advanceWaypoints(prepared.bodies,step.bodies), prepared.combat, dt)
    val events = prepared.events + step.collisions + combatStep.events
    // Arena cleanup applies to spent threats. Player bodies belong to world space,
    // so a launch after camera travel must survive outside the initial arena.
    val corePosition=combatStep.bodies.firstOrNull { it.kind == BodyKind.Core }?.position ?: Vec2(450.0,700.0)
    var bodies = combatStep.bodies.filter {
        val offset=it.position-corePosition
        it.kind != BodyKind.Meteor || offset.x*it.velocity.x+offset.y*it.velocity.y < 0 ||
            (it.position.x in -CullMargin..(current.arena.width + CullMargin) &&
                it.position.y in -CullMargin..(current.arena.height + CullMargin)) }
    var challenge=current.challenge
    if (challenge != null && challenge.ids.isNotEmpty()) {
        val outcomes=events.filter { it.meteorId in challenge!!.ids }.distinctBy { it.meteorId }
        var ids=challenge.ids
        var failed=challenge.failed
        for (event in outcomes) {
            ids=ids-event.meteorId!!
            if (event.secondKind == BodyKind.Core) failed=true
            if (event.meteorId == challenge.parentId && event.secondKind != BodyKind.Core) {
                prepared.bodies.firstOrNull { it.id == event.meteorId }?.let { parent ->
                    val children=fractureChallenge(parent,event.position,corePosition)
                    bodies=bodies+children; ids=ids+children.map { it.id }
                }
            }
        }
        challenge=challenge.copy(ids=ids,failed=failed)
    }
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
    if (challenge != null && challenge.ids.isEmpty() && !challenge.rewarded) {
        if (!challenge.failed && lives > 0) score += 600*current.difficulty.scoreFactor
        challenge=challenge.copy(rewarded=true)
    }
    val elapsed = current.elapsed + dt
    var waveDelay=current.waveDelay
    if (current.wave == 10 && challenge?.ids?.isNotEmpty() == true && elapsed-waveDelay >= 9*28.0+24.0)
        waveDelay=elapsed-(9*28.0+24.0)
    val waveTime=elapsed-waveDelay; val cycle=waveTime % 28.0
    if (current.waveTime % 28.0 < 24.0 && cycle >= 24.0 && challenge?.ids?.isNotEmpty() != true)
        score += (150 + lives * 20) * current.difficulty.scoreFactor
    val wave = 1 + (waveTime / 28.0 + 1e-9).toInt()
    val pending = current.pending.map { it.copy(seconds = it.seconds - dt) }.toMutableList()
    if (lives > 0) bodies = bodies + pending.filter { it.seconds <= 0 }.map { it.body }
    pending.removeAll { it.seconds <= 0 }
    var timer = current.spawnTimer - dt
    if (cycle >= 24.0) timer = 1.5
    if (lives > 0 && cycle < 24.0 && timer <= 0.0) {
        repeat(if (current.practice || wave == 10) 1 else (1 + wave / 3).coerceAtMost(4)) {
            if (pending.size + bodies.count { it.kind == BodyKind.Meteor } < (8 + wave).coerceAtMost(18)) {
                if (current.practice && (pending.isNotEmpty() || bodies.any { it.kind == BodyKind.Meteor })) return@repeat
                val side = if (wave == 1) wave % 4 else (wave + it + random.nextInt(2)) % 4
                var meteor = SimulationEngine.spawnMeteor(Vec2(900.0, 1400.0), wave * 0.4, random, Vec2(450.0, 700.0), side)
                val boss=!current.practice && wave == 10 && challenge == null
                if (boss) {
                    meteor=meteor.copy(mass=1800.0,radius=SimulationEngine.radiusForMass(1800.0),
                        color=androidx.compose.ui.graphics.Color(0xFFFF8B70),velocity=meteor.velocity*.65)
                    challenge=ArcadeChallenge(meteor.id,setOf(meteor.id))
                }
                if (!boss && !current.practice && wave >= 3 && wave % 2 == 1 && it == 0) {
                    val mass = 500.0 + wave * 40.0
                    meteor = meteor.copy(mass = mass, radius = SimulationEngine.radiusForMass(mass), velocity = meteor.velocity * 0.65)
                }
                val incoming=if (current.practice) meteor.copy(position = Vec2(964.0, 700.0), velocity = Vec2(-40.0, 0.0), mass = 90.0,
                    radius = SimulationEngine.radiusForMass(90.0)) else meteor.copy(velocity = meteor.velocity * (0.60 + minOf(wave, 16) * 0.035))
                pending += PendingThreat(distantThreat(incoming,current,view),current.difficulty.warningSeconds)
            }
        }
        timer += (4.6 - wave * 0.25).coerceAtLeast(1.4) * current.difficulty.spawnDelay
    }
    // A missed challenge fragment may leave the arena; it must not freeze the wave forever.
    challenge?.let { active ->
        val present=bodies.map { it.id }.toSet()+pending.map { it.body.id }
        val missing=active.ids-present
        if (missing.isNotEmpty()) challenge=active.copy(ids=active.ids-missing,failed=true)
    }
    return offerUpgrade(current.copy(bodies = bodies, lives = lives, elapsed = elapsed, pending = pending, spawnTimer = timer,
        waveDelay=waveDelay,challenge=challenge,
        energy = (current.energy + dt * current.energyRegen + stopped * 8.0).coerceAtMost(current.maxEnergy), score = score,
        destroyed = current.destroyed + stopped, combo = combo, lastIntercept = lastIntercept, immunity = immunity,
        combat = combatStep.combat, explosions = com.xekep.space.sim.advanceExplosions(current.explosions, events, dt),
        hitFlash = if (hit) 1.0 else (current.hitFlash - dt * 1.8).coerceAtLeast(0.0), successfulLaunches = successful),random)
}
