package com.xekep.space.ui.space

import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import kotlin.math.*

/** Draw-only state. Physics, picking and saves always use the authoritative bodies.
 * Start each transition at the currently drawn position, even when worker timings vary. */
internal class SandboxInterpolation {
    private data class Pose(val start: Vec2,val end: Vec2,val heading: Vec2,val nextHeading: Vec2,
        val parentStart: Vec2? = null,val parentEnd: Vec2? = null)
    private var poses=emptyMap<Long,Pose>()
    private var previous: List<CelestialBody>?=null
    private var generation=Long.MIN_VALUE
    private var publishedAt=0L
    private var duration=16_666_667L
    private var alpha=1.0

    fun begin(bodies: List<CelestialBody>,epoch: Long,now: Long,enabled: Boolean) {
        val elapsed=(now-publishedAt).coerceAtLeast(0L)
        alpha=(elapsed.toDouble()/duration).coerceIn(0.0,1.0)
        if (!enabled || generation != epoch || previous == null) {
            poses=emptyMap(); previous=bodies; generation=epoch; publishedAt=now; alpha=1.0
            return
        }
        if (previous !== bodies) {
            val old=previous!!.associateBy { it.id }
            val next=bodies.associateBy { it.id }
            poses=bodies.associate { body ->
                val before=old[body.id]
                // A merge, undo or new object must never fly from an unrelated old position.
                val continuous=before != null && before.kind == body.kind && abs(before.mass/body.mass-1) < .001
                body.id to if (continuous) Pose(position(before!!),body.position,heading(before),body.heading,
                    if (body.orbitalDetail != null) old[body.orbitParentId]?.let(::position) else null,
                    if (body.orbitalDetail != null) next[body.orbitParentId]?.position else null)
                    else Pose(body.position,body.position,body.heading,body.heading)
            }
            duration=elapsed.coerceIn(16_666_667L,2_000_000_000L)
            publishedAt=now; previous=bodies; alpha=0.0
        }
    }
    fun position(body: CelestialBody): Vec2 = poses[body.id]?.let {
        if (it.parentStart != null && it.parentEnd != null) {
            val start=it.start-it.parentStart; val end=it.end-it.parentEnd
            val angle=atan2(start.y,start.x)
            val turn=atan2(sin(atan2(end.y,end.x)-angle),cos(atan2(end.y,end.x)-angle))
            val radius=start.magnitude()+(end.magnitude()-start.magnitude())*alpha
            it.parentStart+(it.parentEnd-it.parentStart)*alpha+Vec2(cos(angle+turn*alpha),sin(angle+turn*alpha))*radius
        } else it.start+(it.end-it.start)*alpha
    } ?: body.position
    fun heading(body: CelestialBody): Vec2 = poses[body.id]?.let {
        if (it.heading == it.nextHeading) return@let it.nextHeading
        val start=atan2(it.heading.y,it.heading.x)
        val difference=atan2(sin(atan2(it.nextHeading.y,it.nextHeading.x)-start),cos(atan2(it.nextHeading.y,it.nextHeading.x)-start))
        val angle=start+difference*alpha
        Vec2(cos(angle),sin(angle))
    } ?: body.heading
}
