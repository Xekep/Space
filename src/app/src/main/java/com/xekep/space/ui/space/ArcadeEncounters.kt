package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*
import kotlin.random.Random

enum class WaveCharacter { Approach, Swarm, Siege, Pincer, Recovery, Giant, Escort }
enum class ConvoyStatus { Approaching, Delivered, Lost }
data class ArcadeConvoy(val bodyId: Long, val hull: Int = 3, val elapsed: Double = 0.0,
    val status: ConvoyStatus = ConvoyStatus.Approaching, val bonusAwarded: Boolean = false)

internal fun waveCharacter(wave: Int, practice: Boolean = false): WaveCharacter = when {
    practice || wave == 1 -> WaveCharacter.Approach
    wave == 10 -> WaveCharacter.Giant
    wave == 15 -> WaveCharacter.Escort
    wave == 11 || wave == 16 -> WaveCharacter.Recovery
    wave == 2 || wave == 6 -> WaveCharacter.Swarm
    wave == 3 || wave == 9 -> WaveCharacter.Siege
    wave == 5 || wave == 8 -> WaveCharacter.Pincer
    wave < 10 -> WaveCharacter.Recovery
    else -> listOf(WaveCharacter.Recovery,WaveCharacter.Swarm,WaveCharacter.Siege,WaveCharacter.Pincer)[(wave-11)%4]
}
val ArcadeSession.character: WaveCharacter get() = waveCharacter(wave,practice)

internal fun waveGroupSize(wave: Int, character: WaveCharacter): Int = when (character) {
    WaveCharacter.Approach, WaveCharacter.Giant, WaveCharacter.Recovery -> 1
    WaveCharacter.Swarm -> (2+wave/3).coerceAtMost(6)
    WaveCharacter.Pincer -> if (wave < 8) 2 else 4
    WaveCharacter.Escort -> 3
    WaveCharacter.Siege -> (1+wave/3).coerceIn(2,4)
}
internal fun waveSpawnDelay(wave: Int, character: WaveCharacter): Double =
    (4.6-wave*.25).coerceAtLeast(1.4)*when (character) {
        WaveCharacter.Recovery -> 2.1
        WaveCharacter.Swarm -> 1.25
        WaveCharacter.Siege -> 1.35
        WaveCharacter.Escort -> 1.8
        else -> 1.0
    }

internal fun shapeWaveMeteor(body: CelestialBody, wave: Int, index: Int, character: WaveCharacter, random: Random): CelestialBody {
    val mass=when {
        character == WaveCharacter.Swarm -> random.nextDouble(55.0,105.0)
        character == WaveCharacter.Siege && index == 0 -> 500.0+wave*40.0
        character == WaveCharacter.Recovery -> random.nextDouble(70.0,140.0)
        character == WaveCharacter.Escort -> random.nextDouble(90.0,180.0)
        else -> body.mass
    }
    val speed=if (character == WaveCharacter.Siege && index == 0) .65 else if (character == WaveCharacter.Swarm) 1.08 else 1.0
    return body.copy(mass=mass,radius=SimulationEngine.radiusForMass(mass),velocity=body.velocity*speed,
        color=if (character == WaveCharacter.Siege && index == 0) Color(0xFFFFAD6B) else body.color)
}

/** Ballistic lead for the moving transport; threats keep normal gravity after launch.
 * A few bounded shooting corrections account for the core and the orbiting planet.
 */
internal fun aimConvoyThreat(threat: CelestialBody, transport: CelestialBody, scene: List<CelestialBody>, warning: Double): CelestialBody {
    val core=scene.firstOrNull { it.kind == BodyKind.Core } ?: return threat
    val radial=(transport.position-core.position).normalized()
    val distance=(transport.position-core.position).magnitude()
    val speed=210.0
    var time=(threat.position-transport.position).magnitude()/speed
    var target=transport.position
    repeat(6) {
        target=core.position+radial*maxOf(core.radius+transport.radius+95.0,distance-75*(time+warning))
        time=((threat.position-target).magnitude()/speed).coerceIn(2.0,12.0)
    }
    var velocity=(target-threat.position)/time
    val gravity=scene.filter { it.kind == BodyKind.Core || it.kind == BodyKind.ArcadePlanet }
    repeat(4) {
        val trial=NumericIntegrator.advance(gravity+threat.copy(velocity=velocity),time,1.0/30,
            fixedCore=true,recordTrail=false,alignRockets=false).last()
        velocity+=(target-trial.position)/time
        if (velocity.magnitude() > 350.0) velocity=velocity.normalized()*350.0
    }
    return threat.copy(velocity=velocity)
}

/** Introduce each milestone once. The planet remains a normal moving gravitational body. */
internal fun prepareArcadeEncounters(run: ArcadeSession, dt: Double, random: Random): ArcadeSession {
    if (run.practice) return run
    val core=run.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return run
    var bodies=run.bodies
    var planetId=run.planetId
    if (run.wave >= 11 && planetId == null) {
        val existing=bodies.firstOrNull { it.kind == BodyKind.ArcadePlanet }
        val angle=random.nextDouble(2*PI)
        val point=(0 until 12).map { index ->
            val phase=angle+index*PI/6
            core.position+Vec2(cos(phase),sin(phase))*430.0
        }.maxBy { point -> bodies.filter { it.kind != BodyKind.Core }.minOfOrNull {
            (it.position-point).magnitude()-it.radius
        } ?: Double.POSITIVE_INFINITY }
        if (existing != null || bodies.none { (it.position-point).magnitude() <= it.radius+63.0 }) {
            val planet=existing ?: CelestialBody(SimulationEngine.newBodyId(),point,
                SimulationEngine.orbitVelocity(core,point),850.0,27f,Color(0xFF6FAAC9),BodyKind.ArcadePlanet)
            planetId=planet.id
            if (existing == null) bodies=bodies+planet
        }
    }
    var convoy=run.convoy
    if (run.wave >= 15 && convoy == null) {
        val angle=random.nextDouble(2*PI)
        val direction=(0 until 12).map { index -> Vec2(cos(angle+index*PI/6),sin(angle+index*PI/6)) }
            .maxBy { direction -> bodies.minOfOrNull { (it.position-(core.position+direction*1250.0)).magnitude()-it.radius }
                ?: Double.POSITIVE_INFINITY }
        val transport=CelestialBody(SimulationEngine.newBodyId(),core.position+direction*1250.0,
            direction*-75.0,80.0,15f,Color(0xFF82EAC8),BodyKind.Convoy,heading=direction*-1.0)
        bodies=bodies+transport; convoy=ArcadeConvoy(transport.id)
    }
    val planet=bodies.firstOrNull { it.id == planetId }
    if (convoy?.status == ConvoyStatus.Approaching) bodies=bodies.map { body ->
        if (body.id != convoy.bodyId) body else {
            val target=core.position
            var speed=75.0
            if (planet != null) {
                val offset=body.position-core.position
                val planetOffset=planet.position-core.position
                val distance=offset.magnitude(); val orbit=planetOffset.magnitude()
                val radial=offset.normalized(); val planetRadial=planetOffset.normalized()
                val sameSector=radial.x*planetRadial.x+radial.y*planetRadial.y > .4
                // Wait outside the orbit until the planet passes the arrival lane.
                // Steering around a linear prediction can hit a fast orbiting obstacle.
                if (distance in (orbit+140.0)..(orbit+240.0) && sameSector) speed=0.0
            }
            val heading=turnHeading(body.heading,target-body.position,dt,1.8)
            body.copy(heading=heading,velocity=heading*speed)
        }
    }
    return run.copy(bodies=bodies,planetId=planetId,convoy=convoy)
}

/** Optional escort: three hits, a finite arrival window and no core-life penalty on failure. */
internal fun finishArcadeConvoy(before: ArcadeSession, after: ArcadeSession, events: List<CollisionEvent>, dt: Double): ArcadeSession {
    val convoy=after.convoy ?: return after
    if (convoy.status != ConvoyStatus.Approaching) return after.copy(convoy=convoy.copy(elapsed=convoy.elapsed+dt))
    val transport=after.bodies.firstOrNull { it.id == convoy.bodyId }
    val core=after.bodies.firstOrNull { it.kind == BodyKind.Core }
    val hull=(convoy.hull-events.count { it.meteorId != null && it.secondKind == BodyKind.Convoy && it.defenderId == convoy.bodyId }).coerceAtLeast(0)
    val elapsed=convoy.elapsed+dt
    val planetHit=transport != null && after.bodies.filter { it.kind == BodyKind.ArcadePlanet }.any { planet ->
        val old=before.bodies.firstOrNull { it.id == transport.id } ?: transport
        val oldPlanet=before.bodies.firstOrNull { it.id == planet.id } ?: planet
        bodyContact(old,transport,oldPlanet,planet) != null
    }
    val status=when {
        hull <= 0 || transport == null || core == null || after.lives <= 0 || planetHit || elapsed >= 42.0 -> ConvoyStatus.Lost
        (transport.position-core.position).magnitude() <= core.radius+transport.radius+75.0 -> ConvoyStatus.Delivered
        else -> ConvoyStatus.Approaching
    }
    if (status == ConvoyStatus.Approaching) return after.copy(convoy=convoy.copy(hull=hull,elapsed=elapsed))
    val effects=if (status == ConvoyStatus.Lost && transport != null) advanceExplosions(after.explosions,
        listOf(CollisionEvent(BodyKind.Convoy,BodyKind.Meteor,transport.position,vehicleExplosion=true,
            velocity=transport.velocity*.12,seed=transport.id.toInt())),0.0) else after.explosions
    return after.copy(bodies=after.bodies.filterNot { it.id == convoy.bodyId },explosions=effects,
        convoy=convoy.copy(hull=hull,elapsed=0.0,status=status),
        score=after.score+if (status == ConvoyStatus.Delivered) 600*after.difficulty.scoreFactor else 0.0)
}
