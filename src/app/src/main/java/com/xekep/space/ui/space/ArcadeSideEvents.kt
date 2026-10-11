package com.xekep.space.ui.space

import androidx.compose.ui.graphics.Color
import com.xekep.space.sim.*
import kotlin.math.*
import kotlin.random.Random

enum class SideEventKind { Supply, Wreck, Rescue }
enum class SideEventStatus { Active, Completed, Missed }
data class ArcadeSideEvent(val kind: SideEventKind, val position: Vec2, val remaining: Double = 40.0,
    val status: SideEventStatus = SideEventStatus.Active)

internal fun prepareSideEvent(run: ArcadeSession, random: Random): ArcadeSession {
    if (run.practice || run.lives <= 0 || run.wave < run.nextEventWave || run.sideEvent?.status == SideEventStatus.Active ||
        (run.strategy == null && run.wave in listOf(7,10,13,15,20)) || (run.strategy == null && run.wave >= 20) || run.strategy?.victory == true) return run
    val core=run.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return run
    // A separate persisted stream must not alter the composition or timing of attack waves.
    val eventRandom=SessionRandom(run.eventRandomState ?: (random as? SessionRandom)?.state ?: 73L)
    val previous=run.sideEvent?.kind
    val choices=SideEventKind.entries.filter { it != previous }
    val kind=choices[eventRandom.nextInt(choices.size)]
    val phase=eventRandom.nextDouble(2*PI)
    val position=(0..11).map { core.position+Vec2(cos(phase+it*PI/6),sin(phase+it*PI/6))*620.0 }
        .maxBy { point -> run.bodies.minOfOrNull { (it.position-point).magnitude()-it.radius } ?: 0.0 }
    return run.copy(sideEvent=ArcadeSideEvent(kind,position),nextEventWave=run.wave+eventRandom.nextInt(3,6),eventRandomState=eventRandom.state)
}
internal fun advanceSideEvent(before: ArcadeSession, run: ArcadeSession, dt: Double, controlledId: Long?): ArcadeSession {
    val event=run.sideEvent?.takeIf { it.status == SideEventStatus.Active } ?: return run
    val core=run.bodies.firstOrNull { it.kind == BodyKind.Core } ?: return run
    val old=before.bodies.associateBy { it.id }
    val visitors=run.bodies.filter { it.isVehicle && (run.strategy != null || it.id == controlledId || it.waypoints.isNotEmpty() || old[it.id]?.waypoints?.isNotEmpty() == true) }
    var position=event.position
    val completed: Boolean
    if (event.kind == SideEventKind.Rescue) {
        if (visitors.any { (it.position-position).magnitude() <= 250.0 && abs(it.flightHeight) < 80.0 })
            position+=(core.position-position).normalized()*minOf(65.0*dt,(core.position-position).magnitude())
        completed=(position-core.position).magnitude() < core.radius+100.0
    } else completed=visitors.any { body ->
        val previous=old[body.id] ?: body
        val contact=firstSphereContact(previous.position-position,body.position-position,previous.flightHeight,body.flightHeight,body.radius+55.0)
        contact != null && contact*dt <= event.remaining
    }
    val hit=event.kind == SideEventKind.Rescue && run.bodies.any { it.kind == BodyKind.Meteor && (it.position-position).magnitude() < it.radius+25.0 }
    val remaining=maxOf(0.0,event.remaining-dt)
    val status=when { hit -> SideEventStatus.Missed; completed -> SideEventStatus.Completed; remaining == 0.0 -> SideEventStatus.Missed; else -> SideEventStatus.Active }
    var next=run.copy(sideEvent=event.copy(position=position,remaining=remaining,status=status))
    if (status != SideEventStatus.Completed) return next
    next=next.copy(eventsCompleted=run.eventsCompleted+1,score=run.score+250*run.difficulty.scoreFactor,
        energy=minOf(run.maxEnergy,run.energy+45))
    if (event.kind == SideEventKind.Rescue) next=next.copy(lives=minOf(run.difficulty.lives,run.lives+1))
    if (event.kind == SideEventKind.Wreck && run.bodies.count { it.kind == BodyKind.Ship } < run.launchLimit(BodyKind.Ship) &&
        run.bodies.none { (it.position-position).magnitude() < it.radius+35.0 }) {
        val direction=(position-core.position).normalized()
        val ship=CelestialBody(SimulationEngine.newBodyId(),position,direction*100.0,36.0,
            SimulationEngine.radiusForMass(36.0),Color(0xFF8BD3FF),BodyKind.Ship,heading=direction,fuelConsumptionScale=run.fuelScale)
        next=next.copy(bodies=next.bodies+ship,launches=next.launches+1)
    }
    return next
}
