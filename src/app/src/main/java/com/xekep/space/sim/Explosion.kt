package com.xekep.space.sim

import kotlin.math.*
import kotlin.random.Random

data class DebrisParticle(val velocity: Vec2, val size: Float, val spin: Float, val ember: Boolean)
data class Explosion(val position: Vec2, val drift: Vec2, val particles: List<DebrisParticle>, val age: Double = 0.0,val seed: Int = 0,val collapseRadius: Float = 0f) {
    val duration: Double get() = if (collapseRadius > 0f) COLLAPSE_DURATION else DURATION
    companion object {
        const val DURATION = 1.2
        const val COLLAPSE_DURATION = 2.4
        const val MAX_BURSTS = 12
        fun from(event: CollisionEvent): Explosion {
            if (event.collapseRadius > 0f) return Explosion(event.position,event.velocity,emptyList(),
                seed=event.seed,collapseRadius=event.collapseRadius)
            val random = Random(event.seed)
            return Explosion(event.position, event.velocity, List(24) { index ->
                val angle = random.nextDouble(2 * PI)
                DebrisParticle(Vec2(cos(angle), sin(angle)) * random.nextDouble(35.0, 145.0),
                    random.nextDouble(1.0, 3.5).toFloat(), random.nextDouble(-180.0, 180.0).toFloat(), index % 3 == 0)
            },seed=event.seed)
        }
    }
}

fun advanceExplosions(current: List<Explosion>, events: List<CollisionEvent>, seconds: Double): List<Explosion> =
    (current.map { it.copy(age = it.age + seconds) }.filter { it.age < it.duration } +
        events.filter { it.vehicleExplosion || it.debrisImpact || it.collapseRadius > 0f }.takeLast(Explosion.MAX_BURSTS).map(Explosion::from)).takeLast(Explosion.MAX_BURSTS)
