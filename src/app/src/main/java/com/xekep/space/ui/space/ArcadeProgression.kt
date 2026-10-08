package com.xekep.space.ui.space

import com.xekep.space.sim.*
import kotlin.math.pow
import kotlin.random.Random

enum class ArcadeUpgrade { Fleet, Engines, Guns, Reactor, Repair }

data class ArcadeUpgradeOffer(val wave: Int, val choices: List<ArcadeUpgrade>, val convoyBonus: Boolean = false)
data class ArcadeChallenge(val parentId: Long, val ids: Set<Long>, val failed: Boolean = false, val rewarded: Boolean = false)

internal fun ArcadeSession.level(upgrade: ArcadeUpgrade) = upgrades[upgrade] ?: 0
internal val ArcadeSession.fuelScale: Double get() = .85.pow(level(ArcadeUpgrade.Engines))
internal val ArcadeSession.gunIntervalScale: Double get() = .85.pow(level(ArcadeUpgrade.Guns))
val ArcadeSession.maxEnergy: Double get() = MaxEnergy + 20 * level(ArcadeUpgrade.Reactor)
internal val ArcadeSession.energyRegen: Double get() = EnergyRegenPerSecond + 2 * level(ArcadeUpgrade.Reactor)
val ArcadeSession.guardianUnlocked: Boolean get() = !practice && (wave >= 4 || 3 in chosenUpgradeWaves)
internal fun ArcadeSession.launchLimit(kind: BodyKind): Int {
    val tier=if (practice) 0 else when { wave >= 15 -> 3; wave >= 10 -> 2; wave >= 5 -> 1; else -> 0 }
    return when (kind) {
        BodyKind.Ship -> 3 + tier + level(ArcadeUpgrade.Fleet)
        BodyKind.Rocket -> 10 + tier * 2 + level(ArcadeUpgrade.Fleet) * 2
        else -> 30
    }
}

internal fun offerUpgrade(run: ArcadeSession, random: Random): ArcadeSession {
    if (run.practice || run.lives <= 0 || run.upgradeOffer != null) return run
    val bonus=run.convoy?.let { it.status == ConvoyStatus.Delivered && !it.bonusAwarded } == true
    if (!bonus && (!run.resting || run.wave in run.offeredUpgradeWaves ||
        (run.wave !in listOf(3,6,10) && (run.wave < 15 || run.wave % 5 != 0)))) return run
    val choices=ArcadeUpgrade.entries.filter {
        run.level(it) < 2 && (it != ArcadeUpgrade.Repair || run.lives < run.difficulty.lives)
    }.shuffled(random).take(3)
    if (choices.isEmpty()) return if (bonus) run.copy(convoy=run.convoy!!.copy(bonusAwarded=true),energy=run.maxEnergy) else run
    return run.copy(upgradeOffer=ArcadeUpgradeOffer(run.wave,choices,bonus),
        offeredUpgradeWaves=if (bonus) run.offeredUpgradeWaves else run.offeredUpgradeWaves+run.wave,
        convoy=if (bonus) run.convoy!!.copy(bonusAwarded=true) else run.convoy)
}

internal fun selectUpgrade(run: ArcadeSession, upgrade: ArcadeUpgrade): ArcadeSession {
    val offer=run.upgradeOffer ?: return run
    if (upgrade !in offer.choices) return run
    val levels=run.upgrades+(upgrade to run.level(upgrade)+1)
    val next=run.copy(upgrades=levels,upgradeOffer=null,chosenUpgradeWaves=if (offer.convoyBonus) run.chosenUpgradeWaves else run.chosenUpgradeWaves+offer.wave,
        lives=if (upgrade == ArcadeUpgrade.Repair) (run.lives+1).coerceAtMost(run.difficulty.lives) else run.lives)
    return next.copy(energy=(run.energy+if (upgrade == ArcadeUpgrade.Reactor) 20.0 else 0.0).coerceAtMost(next.maxEnergy),
        bodies=run.bodies.map { if (it.isVehicle) it.copy(fuelConsumptionScale=next.fuelScale) else it })
}

/** A single bounded split; children never produce further generations. */
internal fun fractureChallenge(parent: CelestialBody, impact: Vec2, core: Vec2): List<CelestialBody> {
    val direction=(core-impact).normalized().takeIf { it.magnitude() > .5 } ?: parent.velocity.normalized()
    val sideways=direction.perpendicular()
    return List(3) { i ->
        val point=impact+sideways*((i-1)*44.0)-direction*20.0
        val course=(direction+sideways*((i-1)*.35)).normalized()
        val speed=parent.velocity.magnitude().coerceIn(100.0,240.0)
        CelestialBody(SimulationEngine.newBodyId(),point,course*speed,180.0,SimulationEngine.radiusForMass(180.0),
            androidx.compose.ui.graphics.Color(0xFFFFA46B),BodyKind.Meteor)
    }
}
