package com.xekep.space.sim

/** Trail history is presentation data; contacts never alter physics to make a curve smoother. */
internal fun appendMotionTrail(body: CelestialBody,point: Vec2,dense: Boolean): List<Vec2> {
    val limit=when { body.isDebris -> 8; dense && !body.isVehicle -> 20; else -> 42 }
    val trail=body.trail
    if (trail.lastOrNull() == point) return if (trail.size <= limit) trail else trail.takeLast(limit)
    return trail.takeLast(limit-1)+point
}

internal fun contactTrail(body: CelestialBody,contact: Vec2,outgoing: Vec2): List<Vec2> {
    val before=body.velocity.magnitude(); val after=outgoing.magnitude()
    val turn=if (before > 1e-9 && after > 1e-9)
        (body.velocity.x*outgoing.x+body.velocity.y*outgoing.y)/(before*after) else 1.0
    val abrupt=turn < .8 || (outgoing-body.velocity).magnitude() > before.coerceAtLeast(1.0)*.4
    val history=body.trail.takeLast(if (abrupt) 4 else 41)
    return if (history.lastOrNull() == contact) history else history+contact
}
