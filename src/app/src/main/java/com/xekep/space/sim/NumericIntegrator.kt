package com.xekep.space.sim

import kotlin.math.ceil
import kotlin.math.sqrt

/** RK4 with reusable primitive buffers; each pair contributes to both bodies. */
internal object NumericIntegrator {
    private class Buffers(count: Int) {
        val values = DoubleArray(count * 4); val masses = DoubleArray(count); val fixed = BooleanArray(count)
        val k1 = DoubleArray(count * 4); val k2 = DoubleArray(count * 4)
        val k3 = DoubleArray(count * 4); val k4 = DoubleArray(count * 4)
        val temporary = DoubleArray(count * 4); val thrust = DoubleArray(count * 2)
    }
    // Tests and gameplay can run on different threads; never share mutable RK4 buffers.
    private val buffers = ThreadLocal<Buffers>()

    fun advance(bodies: List<CelestialBody>, seconds: Double, limit: Double, fixedCore: Boolean = false,
        recordTrail: Boolean = true): List<CelestialBody> {
        if (bodies.isEmpty() || seconds <= 0.0) return bodies
        val count = bodies.size
        val workspace = buffers.get()?.takeIf { it.masses.size == count } ?: Buffers(count).also { buffers.set(it) }
        val values = workspace.values; val masses = workspace.masses; val fixed = workspace.fixed
        val powered = bodies.any { it.kind == BodyKind.Rocket && it.burnRemaining > 0.0 }
        bodies.forEachIndexed { i, body ->
            masses[i] = body.mass; fixed[i] = fixedCore && body.kind == BodyKind.Core
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
            if (powered) bodies.forEachIndexed { i, body ->
                val acceleration = if (body.kind == BodyKind.Rocket)
                    80.0 * ((body.burnRemaining - step * dt) / dt).coerceIn(0.0, 1.0) else 0.0
                thrust[i * 2] = body.heading.x * acceleration; thrust[i * 2 + 1] = body.heading.y * acceleration
            }
            derivative(values, masses, fixed, thrust, k1)
            for (i in values.indices) temporary[i] = values[i] + k1[i] * dt * 0.5
            derivative(temporary, masses, fixed, thrust, k2)
            for (i in values.indices) temporary[i] = values[i] + k2[i] * dt * 0.5
            derivative(temporary, masses, fixed, thrust, k3)
            for (i in values.indices) temporary[i] = values[i] + k3[i] * dt
            derivative(temporary, masses, fixed, thrust, k4)
            for (i in values.indices) values[i] += (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]) * dt / 6.0
        }
        return bodies.mapIndexed { i, body ->
            val point = Vec2(values[i * 4], values[i * 4 + 1])
            body.copy(position = point, velocity = Vec2(values[i * 4 + 2], values[i * 4 + 3]),
                burnRemaining = (body.burnRemaining - seconds).coerceAtLeast(0.0),
                trail = if (recordTrail) (body.trail.takeLast(41) + point) else body.trail)
        }
    }

    private fun derivative(state: DoubleArray, masses: DoubleArray, fixed: BooleanArray, thrust: DoubleArray, result: DoubleArray) {
        result.fill(0.0)
        for (i in masses.indices) {
            if (!fixed[i]) {
                result[i * 4] = state[i * 4 + 2]; result[i * 4 + 1] = state[i * 4 + 3]
                result[i * 4 + 2] += thrust[i * 2]; result[i * 4 + 3] += thrust[i * 2 + 1]
            }
            for (j in i + 1 until masses.size) {
                val dx = state[j * 4] - state[i * 4]
                val dy = state[j * 4 + 1] - state[i * 4 + 1]
                val square = dx * dx + dy * dy + 18.0 * 18.0
                val force = 400.0 / (square * sqrt(square))
                if (!fixed[i]) { result[i * 4 + 2] += dx * force * masses[j]; result[i * 4 + 3] += dy * force * masses[j] }
                if (!fixed[j]) { result[j * 4 + 2] -= dx * force * masses[i]; result[j * 4 + 3] -= dy * force * masses[i] }
            }
        }
    }
}
