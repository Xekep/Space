package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class OrbitalDetail { RingGrain, ArtificialSatellite }

/** Restricted particles: exact two-body drift with differential external gravity kicks.
 * Their negligible mass does not force every planet to use a millisecond satellite timestep.
 * Positions/velocities are ordinary world states: shaking, edits and nearby holes can unbind them.
 * Planar model: no J2, atmosphere, inclination or collisions between individual grains.
 */
internal object OrbitalDetails {
    fun populate(bodies: MutableList<CelestialBody>, newId: () -> Long) {
        fun add(parent: CelestialBody, radiusKm: Double, phase: Double, type: OrbitalDetail, tint: Long) {
            val distance = radiusKm / AU_KM * AU_WORLD
            val radial = Vec2(cos(phase), sin(phase))
            bodies += CelestialBody(newId(), parent.position + radial * distance,
                parent.velocity + radial.perpendicular() * sqrt(400.0 * parent.mass / distance),
                1e-22, (if (type == OrbitalDetail.RingGrain) .002 else .005).div(AU_KM).times(AU_WORLD).toFloat(),
                Color(tint), physicalScale = true, orbitParentId = parent.id, orbitalDetail = type)
        }
        val saturn = bodies.first { it.solar == SolarBody.Saturn }
        // C, B and A rings, with the Cassini division left empty (NASA ring fact sheet).
        val bands = listOf(74658.0 to 91975.0, 91975.0 to 117507.0, 122340.0 to 136780.0)
        repeat(SolarSystem.RING_GRAINS) { i ->
            val band = bands[if (i < 24) 0 else if (i < 84) 1 else 2]
            val fraction = ((i * .61803398875) % 1.0)
            add(saturn, band.first + (band.second - band.first) * fraction, i * 2.3999632297,
                OrbitalDetail.RingGrain, if (i < 24) 0xFFAD9D83 else if (i < 84) 0xFFEADDC0 else 0xFFD1C9B7)
        }
        val earth = bodies.first { it.solar == SolarBody.Earth }
        // Representative LEO, navigation MEO and geostationary altitudes (ESA).
        val altitudes = listOf(420.0, 550.0, 850.0, 1200.0, 1600.0, 2000.0,
            19100.0, 20200.0, 23222.0, 35786.0, 35786.0, 35786.0)
        altitudes.forEachIndexed { i, altitude ->
            add(earth, SolarBody.Earth.radiusKm + altitude, i * 2.3999632297,
                OrbitalDetail.ArtificialSatellite, 0xFFABCFE4)
        }
    }

    fun advance(before: List<CelestialBody>, mainResult: StepResult, seconds: Double): StepResult {
        if (seconds <= 0) return StepResult(before, mainResult.collisions)
        val old = before.associateBy { it.id }
        var main = mainResult.bodies
        val events = mainResult.collisions.toMutableList()
        val particles = mutableListOf<CelestialBody>()
        val steps = ceil(seconds * 120).toInt().coerceAtLeast(1)
        val dt = seconds / steps
        val hasHoles = main.any { it.kind == BodyKind.BlackHole }
        // Interpolate attractors once for every shared local time, not for every grain/kick.
        val sources = List(steps + 1) { step -> main.map { body ->
            val previous = old[body.id] ?: body
            body.copy(position = previous.position + (body.position - previous.position) * (step.toDouble() / steps),
                velocity = previous.velocity + (body.velocity - previous.velocity) * (step.toDouble() / steps))
        }.associateBy { it.id } }
        for (original in before.filter { it.orbitalDetail != null }) {
            var particle = original
            var alive = true
            for (step in 0 until steps) {
                val parent = main.firstOrNull { it.id == particle.orbitParentId }
                val a = parent?.let { sources[step][it.id] }
                val b = parent?.let { sources[step + 1][it.id] }
                fun gravity(point: Vec2, source: CelestialBody): Vec2 {
                    val delta = source.position - point
                    val square = delta.x * delta.x + delta.y * delta.y + .000001
                    return delta * (400.0 * source.gravityMass / (square * sqrt(square)))
                }
                fun tide(relative: Vec2, time: Int, center: CelestialBody): Vec2 = sources[time].values.fold(Vec2.Zero) { sum, body ->
                    if (body.id == center.id) sum else {
                        sum + gravity(center.position + relative, body) - gravity(center.position, body)
                    }
                }
                val previous = particle
                if (a != null && b != null) {
                    val r = particle.position - a.position
                    val v = particle.velocity - a.velocity + tide(r, step, a) * (dt * .5)
                    val drift = keplerDrift(r, v, 400.0 * parent.gravityMass, dt)
                    if (drift != null) {
                        particle = particle.copy(position = b.position + drift.first,
                            velocity = b.velocity + drift.second + tide(drift.first, step + 1, b) * (dt * .5),
                            heading = drift.second.normalized())
                    } else {
                        // An unbound particle re-enters the ordinary integrator on the next frame.
                        particle = particle.copy(orbitalDetail = null, orbitParentId = null)
                        particle = NumericIntegrator.advance(listOf(particle) + main, dt,
                            SimulationEngine.sandboxStepLimit(listOf(particle) + main), recordTrail = false).first()
                    }
                    if ((particle.position - b.position).magnitude() <= b.radius + particle.radius) {
                        alive = false
                        break
                    }
                } else {
                    particle = particle.copy(orbitalDetail = null, orbitParentId = null)
                    particle = NumericIntegrator.advance(listOf(particle) + main, dt,
                        SimulationEngine.sandboxStepLimit(listOf(particle) + main), recordTrail = false).first()
                }
                if (!hasHoles) continue
                val holes = main.filter { it.kind == BodyKind.BlackHole }
                val absorption = absorbBlackHoles(holes.map { sources[step][it.id] ?: it } + previous,
                    holes.map { (sources[step + 1][it.id] ?: it).copy(mass=it.mass,radius=it.radius) } + particle)
                val survivors = absorption.bodies.associateBy { it.id }
                if (particle.id !in survivors) {
                    // Retain the actual end-of-frame positions of massive bodies.
                    main = main.map { body -> survivors[body.id]?.let { body.copy(mass = it.mass, radius = it.radius,velocity=it.velocity) } ?: body }
                    events += absorption.collisions
                    alive = false
                    break
                }
            }
            if (alive) particles += particle.copy(trail = listOf(particle.position))
        }
        val combined = (main + particles).associateBy { it.id }
        val ordered = before.mapNotNull { combined[it.id] } + main.filter { it.id !in old }
        return StepResult(ordered, events)
    }

    /** Exact elliptic propagation from the current osculating elements, including reverse orbits. */
    internal fun keplerDrift(r: Vec2, v: Vec2, mu: Double, dt: Double): Pair<Vec2, Vec2>? {
        val distance = r.magnitude()
        if (distance < 1e-12 || mu <= 0) return null
        val energy = v.x * v.x + v.y * v.y
        val inverseAxis = 2 / distance - energy / mu
        if (inverseAxis <= 0) return null
        val axis = 1 / inverseAxis
        val eVector = (r * (energy - mu / distance) - v * (r.x * v.x + r.y * v.y)) / mu
        val e = eVector.magnitude()
        if (e >= .999 || !axis.isFinite()) return null
        val x = if (e > 1e-10) eVector / e else r / distance
        val y = x.perpendicular() * if (r.x * v.y - r.y * v.x < 0) -1.0 else 1.0
        val minor = sqrt(1 - e * e)
        val anomaly = if (e > 1e-10) atan2((r.x * y.x + r.y * y.y) / (axis * minor),
            (r.x * x.x + r.y * x.y) / axis + e) else 0.0
        val motion = sqrt(mu / axis.pow(3))
        val mean = (anomaly - e * sin(anomaly) + motion * dt).rem(2 * PI)
        var eccentric = mean
        repeat(12) { eccentric -= (eccentric - e * sin(eccentric) - mean) / (1 - e * cos(eccentric)) }
        val rate = motion / (1 - e * cos(eccentric))
        return (x * (axis * (cos(eccentric) - e)) + y * (axis * minor * sin(eccentric))) to
            (x * (-axis * sin(eccentric) * rate) + y * (axis * minor * cos(eccentric) * rate))
    }
}
