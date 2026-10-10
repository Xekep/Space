package com.xekep.space.ui.space

import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.SimulationEngine
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.depthTimeScale
import kotlin.random.Random

internal fun advanceArcade(input: ArcadeSession, dt: Double, random: Random, control: com.xekep.space.sim.ManualFlightControl? = null, view: ThreatView? = null): ArcadeSession {
    if (input.upgradeOffer != null || input.lives <= 0 || dt <= 0.0) return input
    val current=prepareCampaign(prepareArcadeEncounters(input,dt,random),random)
    val routed=com.xekep.space.sim.applyFlightControls(current.bodies,control,dt)
    val prepared = prepareCombat(routed, current.combat, dt, control?.bodyId,current.gunIntervalScale,current.carrier?.nodeIds.orEmpty())
    val step = SimulationEngine.stepArcade(prepared.bodies, dt,control?.bodyId,control?.depthTimeScale ?: 1.0)
    val impacts=current.carrier?.let { resolveCarrierContacts(prepared.bodies,step.bodies,step.collisions,it) } ?: step
    val combatStep = advanceProjectiles(com.xekep.space.sim.advanceWaypoints(prepared.bodies,impacts.bodies), prepared.combat, dt)
    val blast=heavyRocketBlasts(prepared.bodies,combatStep.bodies,step.collisions)
    val events = prepared.events + impacts.collisions + combatStep.events + blast.second
    // Arena cleanup applies to spent threats. Player bodies belong to world space,
    // so a launch after camera travel must survive outside the initial arena.
    val corePosition=combatStep.bodies.firstOrNull { it.kind == BodyKind.Core }?.position ?: Vec2(450.0,700.0)
    val convoyPosition=combatStep.bodies.firstOrNull { it.id == current.convoy?.bodyId }?.position
    var bodies = blast.first.filter {
        val offset=it.position-corePosition
        val escort=convoyPosition?.minus(it.position)
        it.id in current.carrier?.nodeIds.orEmpty() || it.kind != BodyKind.Meteor || offset.x*it.velocity.x+offset.y*it.velocity.y < 0 ||
            (escort != null && escort.x*it.velocity.x+escort.y*it.velocity.y > 0) ||
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
    step.collisions.filter { it.meteorId in current.carrier?.nodeIds.orEmpty() }.forEach { it.defenderId?.let(successful::add) }
    events.filter { it.meteorId != null }.distinctBy { it.meteorId }.forEach { event ->
        if (event.secondKind == BodyKind.Convoy) return@forEach
        if (event.secondKind == BodyKind.Core) {
            if (immunity <= 0.0) { if (!current.practice) lives = (lives - 1).coerceAtLeast(0); immunity = 0.6; hit = true; combo = 1.0 }
        } else {
            stopped++; score += 100.0 * combo * current.difficulty.scoreFactor
            combo = (combo + 0.25).coerceAtMost(3.0); lastIntercept = current.elapsed
            if (event.secondKind == BodyKind.Player || event.secondKind == BodyKind.Ship || event.secondKind == BodyKind.Rocket) event.defenderId?.let(successful::add)
        }
    }
    // A missed challenge fragment may leave the arena; it must not freeze the wave forever.
    challenge?.let { active ->
        val present=bodies.map { it.id }.toSet()+current.pending.map { it.body.id }
        val missing=active.ids-present
        if (missing.isNotEmpty()) challenge=active.copy(ids=active.ids-missing,failed=true)
    }
    val resolvedChallenge=challenge
    if (resolvedChallenge != null && resolvedChallenge.ids.isEmpty() && !resolvedChallenge.rewarded) {
        if (!resolvedChallenge.failed && lives > 0) score += 600*current.difficulty.scoreFactor
        challenge=resolvedChallenge.copy(rewarded=true)
    }
    val elapsed = current.elapsed + dt
    val pending = current.pending.map { it.copy(seconds = it.seconds - dt) }.toMutableList()
    if (lives > 0) bodies = bodies + pending.filter { it.seconds <= 0 }.map { it.body }
    pending.removeAll { it.seconds <= 0 }
    val waveDelay=campaignWaveDelay(current,bodies,pending,challenge,elapsed)
    val phase=arcadeWavePhase(elapsed-waveDelay); val cycle=phase.seconds
    val quiet=phase.resting && pending.isEmpty() && bodies.none { it.kind == BodyKind.Meteor } &&
        challenge?.ids?.isNotEmpty() != true && !(current.wave == 20 && current.carrier?.defeated == false)
    if (!current.resting && quiet && !hit && !(current.wave == 10 && challenge?.failed == true) && current.wave == phase.wave)
        score += (150 + lives * 20) * current.difficulty.scoreFactor
    val wave = phase.wave
    var timer = current.spawnTimer - dt
    if (cycle >= 24.0 && wave != 20) timer = 1.5
    if (lives > 0 && (waveMaySpawn(wave,cycle,challenge,current.practice) || (wave == 20 && current.carrier?.defeated == false)) && (wave != 20 || current.carrier?.defeated == false) && timer <= 0.0) {
        val character=waveCharacter(wave,current.practice)
        val attackSide=random.nextInt(4)
        repeat(waveGroupSize(wave,character)) {
            if (pending.size + bodies.count { it.kind == BodyKind.Meteor } < (8 + wave).coerceAtMost(18)) {
                if (current.practice && (pending.isNotEmpty() || bodies.any { it.kind == BodyKind.Meteor })) return@repeat
                val side = if (character == WaveCharacter.Pincer) (attackSide+(it%2)*2)%4
                    else if (wave == 1) wave % 4 else (wave + it + random.nextInt(2)) % 4
                val transport=if (character == WaveCharacter.Escort && it < 2)
                    bodies.firstOrNull { body -> body.id == current.convoy?.bodyId } else null
                val target=transport?.position ?: corePosition
                var meteor = SimulationEngine.spawnMeteor(Vec2(900.0, 1400.0), wave * 0.4, random, target, side)
                val boss=!current.practice && wave == 10 && challenge == null
                if (boss) {
                    meteor=meteor.copy(mass=1800.0,radius=SimulationEngine.radiusForMass(1800.0),
                        color=androidx.compose.ui.graphics.Color(0xFFFF8B70),velocity=meteor.velocity*.65)
                    challenge=ArcadeChallenge(meteor.id,setOf(meteor.id))
                }
                if (!boss && !current.practice) meteor=shapeWaveMeteor(meteor,wave,it,character,random)
                val incoming=if (current.practice) meteor.copy(position = Vec2(964.0, 700.0), velocity = Vec2(-40.0, 0.0), mass = 90.0,
                    radius = SimulationEngine.radiusForMass(90.0)) else meteor.copy(velocity = meteor.velocity * (0.60 + minOf(wave, 16) * 0.035))
                // Flanking ambushes meet the transport outside the core patrol, with the
                // same off-screen lead and warning as ordinary threats. No homing after spawn.
                val approach=if (transport != null) {
                    val radial=(transport.position-corePosition).normalized()
                    val flank=(radial*.45+radial.perpendicular()*(if (it == 0) 1.0 else -1.0)).normalized()
                    incoming.copy(position=transport.position+flank*1100.0,velocity=flank*-210.0)
                } else incoming
                // Unlike ambient attacks, carrier volleys visibly originate at its known position.
                val distant=if (character == WaveCharacter.Carrier && current.carrier != null) {
                    val radial=(current.carrierPosition-corePosition).normalized()
                    val point=current.carrierPosition+radial.perpendicular()*((it-.5)*90.0)
                    approach.copy(position=point,velocity=(corePosition-point).normalized()*130.0,trail=listOf(point))
                } else distantThreat(approach,current,view,target)
                val threat=if (transport != null) aimConvoyThreat(distant,transport,bodies,current.difficulty.warningSeconds,
                    current.convoy?.elapsed ?: 0.0) else distant
                pending += PendingThreat(threat,current.difficulty.warningSeconds)
            }
        }
        timer += waveSpawnDelay(wave,waveCharacter(wave,current.practice),current.difficulty) * current.difficulty.spawnDelay
    }
    if (!waveMaySpawn(wave,cycle,challenge,current.practice) && !(wave == 20 && current.carrier?.defeated == false)) timer=maxOf(timer,.25)
    val advanced=current.copy(bodies = bodies, lives = lives, elapsed = elapsed, pending = pending, spawnTimer = timer,
        waveDelay=waveDelay,challenge=challenge,
        energy = (current.energy + dt * current.energyRegen + stopped * 8.0).coerceAtMost(current.maxEnergy), score = score,
        destroyed = current.destroyed + stopped, combo = combo, lastIntercept = lastIntercept, immunity = immunity,
        combat = combatStep.combat, explosions = com.xekep.space.sim.advanceExplosions(current.explosions, events, dt),
        hitFlash = if (hit) 1.0 else (current.hitFlash - dt * 1.8).coerceAtLeast(0.0), successfulLaunches = successful)
    return offerUpgrade(finishCampaign(current,finishArcadeConvoy(current,advanced,events,dt),dt,control?.bodyId),random)
}
