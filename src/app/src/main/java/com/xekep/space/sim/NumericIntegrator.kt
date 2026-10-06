package com.xekep.space.sim

import kotlin.math.ceil
import kotlin.math.sqrt

/** RK4 with reusable primitive buffers; each pair contributes to both bodies. */
internal object NumericIntegrator {
    private class Buffers(count: Int) {
        val values = DoubleArray(count * 4); val masses = DoubleArray(count); val fixed = BooleanArray(count)
        val smoothing = DoubleArray(count); val headings = DoubleArray(count * 2)
        val k1 = DoubleArray(count * 4); val k2 = DoubleArray(count * 4)
        val k3 = DoubleArray(count * 4); val k4 = DoubleArray(count * 4)
        val temporary = DoubleArray(count * 4); val thrust = DoubleArray(count * 2)
    }
    // Tests and gameplay can run on different threads; never share mutable RK4 buffers.
    private val buffers = ThreadLocal<Buffers>()

    fun advance(bodies: List<CelestialBody>, seconds: Double, limit: Double, fixedCore: Boolean = false,
        recordTrail: Boolean = true, alignRockets: Boolean = true, controlledId: Long? = null): List<CelestialBody> {
        if (bodies.isEmpty() || seconds <= 0.0) return bodies
        val count = bodies.size
        val workspace = buffers.get()?.takeIf { it.masses.size == count } ?: Buffers(count).also { buffers.set(it) }
        val values = workspace.values; val masses = workspace.masses; val fixed = workspace.fixed; val smoothing = workspace.smoothing
        val headings = workspace.headings
        val hasRockets = bodies.any { it.kind == BodyKind.Rocket }
        bodies.forEachIndexed { i, body ->
            headings[i * 2] = body.heading.x; headings[i * 2 + 1] = body.heading.y
            smoothing[i] = if (body.physicalScale) .01 else 18.0
            masses[i] = body.gravityMass; fixed[i] = fixedCore && body.kind == BodyKind.Core
            values[i * 4] = body.position.x; values[i * 4 + 1] = body.position.y
            values[i * 4 + 2] = if (fixed[i]) 0.0 else body.velocity.x
            values[i * 4 + 3] = if (fixed[i]) 0.0 else body.velocity.y
        }
        val k1 = workspace.k1; val k2 = workspace.k2; val k3 = workspace.k3; val k4 = workspace.k4
        val temporary = workspace.temporary; val thrust = workspace.thrust
        thrust.fill(0.0)
        val steps = ceil(seconds / limit - 1e-9).toInt().coerceAtLeast(1)
        val dt = seconds / steps
        repeat(steps) { step ->
            if (hasRockets) bodies.forEachIndexed { i, body ->
                if (alignRockets && body.kind == BodyKind.Rocket && body.id != controlledId && body.waypoints.isEmpty()) {
                    val velocity = Vec2(values[i * 4 + 2], values[i * 4 + 3])
                    if (velocity.magnitude() > 2.0) {
                        val heading = turnHeading(Vec2(headings[i * 2], headings[i * 2 + 1]), velocity, dt, 2.8)
                        headings[i * 2] = heading.x; headings[i * 2 + 1] = heading.y
                    }
                }
                val acceleration = if (body.kind == BodyKind.Rocket && body.id != controlledId)
                    80.0 * ((body.burnRemaining - step * dt) / dt).coerceIn(0.0, 1.0) else 0.0
                thrust[i * 2] = headings[i * 2] * acceleration; thrust[i * 2 + 1] = headings[i * 2 + 1] * acceleration
            }
            derivative(values, masses, fixed, smoothing, thrust, k1)
            for (i in values.indices) temporary[i] = values[i] + k1[i] * dt * 0.5
            derivative(temporary, masses, fixed, smoothing, thrust, k2)
            for (i in values.indices) temporary[i] = values[i] + k2[i] * dt * 0.5
            derivative(temporary, masses, fixed, smoothing, thrust, k3)
            for (i in values.indices) temporary[i] = values[i] + k3[i] * dt
            derivative(temporary, masses, fixed, smoothing, thrust, k4)
            for (i in values.indices) values[i] += (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]) * dt / 6.0
        }
        return bodies.mapIndexed { i, body ->
            val point = Vec2(values[i * 4], values[i * 4 + 1])
            body.copy(position = point, velocity = Vec2(values[i * 4 + 2], values[i * 4 + 3]),
                heading = Vec2(headings[i * 2], headings[i * 2 + 1]),
                burnRemaining = (body.burnRemaining - seconds).coerceAtLeast(0.0),
                trail = if (recordTrail) (body.trail.takeLast(41) + point) else body.trail)
        }
    }

    private fun derivative(state: DoubleArray, masses: DoubleArray, fixed: BooleanArray, smoothing: DoubleArray, thrust: DoubleArray, result: DoubleArray) {
        result.fill(0.0)
        for (i in masses.indices) {
            if (!fixed[i]) {
                result[i * 4] = state[i * 4 + 2]; result[i * 4 + 1] = state[i * 4 + 3]
                result[i * 4 + 2] += thrust[i * 2]; result[i * 4 + 3] += thrust[i * 2 + 1]
            }
            for (j in i + 1 until masses.size) {
                val dx = state[j * 4] - state[i * 4]
                val dy = state[j * 4 + 1] - state[i * 4 + 1]
                val soften = minOf(smoothing[i], smoothing[j])
                val square = dx * dx + dy * dy + soften * soften
                val force = 400.0 / (square * sqrt(square))
                if (!fixed[i]) { result[i * 4 + 2] += dx * force * masses[j]; result[i * 4 + 3] += dy * force * masses[j] }
                if (!fixed[j]) { result[j * 4 + 2] -= dx * force * masses[i]; result[j * 4 + 3] -= dy * force * masses[i] }
            }
        }
    }
}
