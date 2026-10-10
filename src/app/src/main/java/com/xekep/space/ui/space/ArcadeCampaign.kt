package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*
import kotlin.random.Random

enum class SalvageStatus { Available, Collected, Expired }
data class ArcadeSalvage(val wave: Int, val position: Vec2, val remaining: Double = 32.0,
    val status: SalvageStatus = SalvageStatus.Available, val rewarded: Boolean = false)
data class ArcadeCarrier(val nodeIds: Set<Long>, val angle: Double, val elapsed: Double = 0.0,
    val defeated: Boolean = false)

internal val ArcadeSession.carrierDeadline: Double get() = when (difficulty) {
    ArcadeDifficulty.Easy -> 100.0; ArcadeDifficulty.Normal -> 90.0; ArcadeDifficulty.Hard -> 80.0
}
internal val ArcadeSession.carrierNodeHull: Double get() = when (difficulty) {
    ArcadeDifficulty.Easy -> 420.0; ArcadeDifficulty.Normal -> 540.0; ArcadeDifficulty.Hard -> 660.0
}
internal val ArcadeSession.carrierPosition: Vec2 get() {
    val core=bodies.firstOrNull { it.kind == BodyKind.Core }?.position ?: Vec2(450.0,700.0)
    val phase=(carrier?.angle ?: 0.0)+(carrier?.elapsed ?: 0.0)*.055
    return core+Vec2(cos(phase),sin(phase))*1100.0
}
internal val ArcadeSession.carrierNodesRemaining: Int get() = bodies.count { it.id in carrier?.nodeIds.orEmpty() }
// Old recovered runs beyond wave 20 predate the carrier and keep their earned result.
internal val ArcadeSession.campaignCleared: Boolean get() = !practice && lives > 0 && wave >= 21 &&
    (carrier == null || carrier.defeated)

/** Optional sorties use the same fleet, fuel, routes and upgrade choices as defence. */
internal fun prepareCampaign(run: ArcadeSession, random: Random): ArcadeSession {
    if (run.practice || run.lives <= 0) return run
    val core=run.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return run
    var next=run
    if (run.wave in listOf(7,13) && run.salvage?.wave != run.wave) {
        val phase=random.nextDouble(2*PI)
        val position=(0 until 12).map { i -> core.position+Vec2(cos(phase+i*PI/6),sin(phase+i*PI/6))*620.0 }
            .maxBy { point -> run.bodies.minOfOrNull { (it.position-point).magnitude()-it.radius } ?: 0.0 }
        next=next.copy(salvage=ArcadeSalvage(run.wave,position))
    }
    if (run.wave == 20 && run.carrier == null) {
        val carrier=ArcadeCarrier(List(3) { SimulationEngine.newBodyId() }.toSet(),random.nextDouble(2*PI))
        next=next.copy(carrier=carrier)
        val mass=run.carrierNodeHull
        val nodes=carrier.nodeIds.mapIndexed { index,id ->
            val phase=carrier.angle+index*2*PI/3
            CelestialBody(id,next.carrierPosition+Vec2(cos(phase),sin(phase))*64.0,Vec2.Zero,
                mass,SimulationEngine.radiusForMass(mass),Color(0xFFFF8B70),BodyKind.Meteor)
        }
        next=next.copy(bodies=next.bodies+nodes,spawnTimer=6.0)
    }
    return positionCarrierNodes(next)
}

/** An armoured node cannot be erased by a tiny projectile body touching it.
 * The craft still explodes; only an actual hull kill produces an interception event.
 */
internal fun resolveCarrierContacts(before: List<CelestialBody>, after: List<CelestialBody>, contacts: List<CollisionEvent>,
    carrier: ArcadeCarrier): StepResult {
    val bodies=after.toMutableList()
    val originals=before.associateBy { it.id }
    val events=contacts.map { event ->
        val node=originals[event.meteorId]
        val attacker=originals[event.defenderId]
        if (node == null || node.id !in carrier.nodeIds || attacker == null ||
            event.secondKind !in listOf(BodyKind.Rocket,BodyKind.Ship,BodyKind.Player,BodyKind.Ambient)) event else {
            val damage=if (attacker.kind == BodyKind.Rocket)
                (if (attacker.hullClass == VehicleHullClass.Heavy) 400.0 else 220.0)*attacker.vehicleDamageScale
                else attacker.mass*.85
            val remaining=node.mass-damage
            if (remaining < 45.0) event else {
                bodies.removeAll { it.id == node.id }
                bodies+=node.copy(mass=remaining,radius=SimulationEngine.radiusForMass(remaining))
                event.copy(meteorId=null)
            }
        }
    }
    return StepResult(bodies,events)
}

/** The carrier holds formation; damage, targeting and collisions still use ordinary combat. */
internal fun positionCarrierNodes(run: ArcadeSession): ArcadeSession {
    val carrier=run.carrier?.takeUnless { it.defeated } ?: return run
    val center=run.carrierPosition
    val phase=carrier.angle+carrier.elapsed*.055
    val ids=carrier.nodeIds.toList()
    return run.copy(bodies=run.bodies.map { body ->
        val index=ids.indexOf(body.id)
        if (index < 0) body else {
            val radial=Vec2(cos(phase+index*2*PI/3),sin(phase+index*2*PI/3))*64.0
            val core=run.bodies.first { it.kind == BodyKind.Core }.position
            body.copy(position=center+radial,velocity=(center-core+radial).perpendicular()*.055)
        }
    })
}

internal fun finishCampaign(before: ArcadeSession, after: ArcadeSession, dt: Double, controlledId: Long?): ArcadeSession {
    var next=after
    after.salvage?.takeIf { it.status == SalvageStatus.Available }?.let { salvage ->
        // A deliberate route or manual sortie is required. Core patrol cannot collect it passively.
        val collected=after.bodies.any { body ->
            val old=before.bodies.firstOrNull { it.id == body.id } ?: body
            val intentional=body.id == controlledId || body.waypoints.isNotEmpty() || old.waypoints.isNotEmpty()
            if (!body.isVehicle || !intentional) false else {
                val contact=firstSphereContact(old.position-salvage.position,body.position-salvage.position,
                    old.flightHeight,body.flightHeight,body.radius+55.0)
                contact != null && contact*dt <= salvage.remaining
            }
        }
        val remaining=(salvage.remaining-dt).coerceAtLeast(0.0)
        val status=when { collected -> SalvageStatus.Collected
            remaining <= 0.0 -> SalvageStatus.Expired; else -> SalvageStatus.Available }
        next=next.copy(salvage=salvage.copy(remaining=remaining,status=status),
            salvageCollected=next.salvageCollected+if (status == SalvageStatus.Collected) 1 else 0)
    }
    after.carrier?.takeUnless { it.defeated }?.let { carrier ->
        val defeated=after.bodies.none { it.id in carrier.nodeIds }
        val elapsed=carrier.elapsed+dt
        next=next.copy(carrier=carrier.copy(elapsed=elapsed,defeated=defeated),
            score=next.score+if (defeated && next.lives > 0) 1500.0*next.difficulty.scoreFactor else 0.0,
            lives=if (!defeated && elapsed >= next.carrierDeadline) 0 else next.lives)
        if (defeated) next=next.copy(explosions=advanceExplosions(next.explosions,
            listOf(CollisionEvent(BodyKind.Meteor,BodyKind.Ship,after.carrierPosition,vehicleExplosion=true,seed=carrier.nodeIds.first().toInt())),0.0))
    }
    return positionCarrierNodes(next)
}

/** Stop the wave clock at cleanup, then grant four genuinely quiet seconds. */
internal fun campaignWaveDelay(run: ArcadeSession, bodies: List<CelestialBody>, pending: List<PendingThreat>,
    challenge: ArcadeChallenge?, elapsed: Double): Double {
    if (run.practice) return run.waveDelay
    val unresolved=bodies.any { it.kind == BodyKind.Meteor } || pending.isNotEmpty() ||
        challenge?.ids?.isNotEmpty() == true || run.carrier?.let { !it.defeated && run.wave == 20 } == true
    val cutoff=(run.wave-1)*28.0+24.0
    return if (unresolved && elapsed-run.waveDelay >= cutoff) elapsed-cutoff else run.waveDelay
}
