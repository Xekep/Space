package com.xekep.space.sim

import kotlin.math.*

/** Hide the catalogue ellipse when the actual osculating orbit differs substantially. */
fun retainsSolarOrbit(body: CelestialBody, parent: CelestialBody): Boolean {
    val entry = body.solar ?: return false
    val r = body.position - parent.position
    val v = body.velocity - parent.velocity
    val distance = r.magnitude()
    if (distance < 1e-9) return false
    val mu = SimulationEngine.gravitationalConstant * (parent.gravityMass + body.gravityMass)
    val speed2 = v.x*v.x + v.y*v.y
    val radial = r.x*v.x + r.y*v.y
    val reciprocalAxis = 2 / distance - speed2 / mu
    if (reciprocalAxis <= 0) return false
    val axis = 1 / reciprocalAxis
    val eccentricity = (r * (speed2 - mu / distance) - v * radial) / mu
    val angle = entry.periapsis * PI / 180
    val expected = Vec2(cos(angle), sin(angle)) * entry.eccentricity
    return abs(axis / (entry.axisAu * AU_WORLD * body.solarOrbitScale) - 1) < .10 && (eccentricity - expected).magnitude() < .10
}
