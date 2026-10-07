package com.xekep.space.sim

import kotlin.math.ceil
import kotlin.math.sqrt

/** RK4 for small scenes; tree gravity with velocity Verlet for large scenes. Buffers are reused. */
internal object NumericIntegrator {
    private class Buffers(count: Int) {
        val values = DoubleArray(count * 4); val masses = DoubleArray(count); val fixed = BooleanArray(count)
        val smoothing = DoubleArray(count); val headings = DoubleArray(count * 2)
        val k1 = DoubleArray(count * 4); val k2 = DoubleArray(count * 4)
        val k3 = DoubleArray(count * 4); val k4 = DoubleArray(count * 4)
        val temporary = DoubleArray(count * 4); val thrust = DoubleArray(count * 2)
        val gravity=DoubleArray(count*2); val tree=BarnesHutGravity()
    }
    // Tests and gameplay can run on different threads; never share mutable RK4 buffers.
    private val buffers = ThreadLocal<Buffers>()

    fun advance(bodies: List<CelestialBody>, seconds: Double, limit: Double, fixedCore: Boolean = false,
        recordTrail: Boolean = true, alignRockets: Boolean = true, controlledId: Long? = null,
        approximateGravity: Boolean = true): List<CelestialBody> {
        if (bodies.isEmpty() || seconds <= 0.0) return bodies
        val count = bodies.size
        val workspace = buffers.get()?.takeIf { it.masses.size == count } ?: Buffers(count).also { buffers.set(it) }
        val values = workspace.values; val masses = workspace.masses; val fixed = workspace.fixed; val smoothing = workspace.smoothing
        val headings = workspace.headings
        val hasVehicles = bodies.any { it.isVehicle }
        val hasRoutes = bodies.any { it.routePath != null }
        val useTree=approximateGravity && count >= BARNES_HUT_THRESHOLD
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
            if (hasVehicles) bodies.forEachIndexed { i, body ->
                if (alignRockets && body.isVehicle && body.id != controlledId && body.waypoints.isEmpty()) {
                    val velocity = Vec2(values[i * 4 + 2], values[i * 4 + 3])
                    if (velocity.magnitude() > 2.0) {
                        val heading = turnHeading(Vec2(headings[i * 2], headings[i * 2 + 1]), velocity, dt, 2.8)
                        headings[i * 2] = heading.x; headings[i * 2 + 1] = heading.y
                    }
                }
                val acceleration = if (body.kind == BodyKind.Rocket && body.id != controlledId && body.routePath == null) {
                    val speed = Vec2(values[i*4+2], values[i*4+3]).magnitude()
                    minOf(80.0 * ((body.fuelRemaining-step*dt)/dt).coerceIn(0.0,1.0),
                        (900.0-speed).coerceAtLeast(0.0)/dt)
                } else 0.0
                thrust[i * 2] = headings[i * 2] * acceleration; thrust[i * 2 + 1] = headings[i * 2 + 1] * acceleration
            }
            if (useTree) {
                // Two tree evaluations per step; the symplectic kick-drift-kick scheme avoids
                // four expensive RK stages while retaining good long-term orbital behaviour.
                derivative(values,masses,fixed,smoothing,thrust,k1,workspace,true)
                for (i in masses.indices) if (!fixed[i]) {
                    values[i*4+2]+=k1[i*4+2]*dt*.5; values[i*4+3]+=k1[i*4+3]*dt*.5
                    values[i*4]+=values[i*4+2]*dt; values[i*4+1]+=values[i*4+3]*dt
                }
                derivative(values,masses,fixed,smoothing,thrust,k2,workspace,true)
                for (i in masses.indices) if (!fixed[i]) {
                    values[i*4+2]+=k2[i*4+2]*dt*.5; values[i*4+3]+=k2[i*4+3]*dt*.5
                }
            } else {
            derivative(values, masses, fixed, smoothing, thrust, k1,workspace,false)
            for (i in values.indices) temporary[i] = values[i] + k1[i] * dt * 0.5
            derivative(temporary, masses, fixed, smoothing, thrust, k2,workspace,false)
            for (i in values.indices) temporary[i] = values[i] + k2[i] * dt * 0.5
            derivative(temporary, masses, fixed, smoothing, thrust, k3,workspace,false)
            for (i in values.indices) temporary[i] = values[i] + k3[i] * dt
            derivative(temporary, masses, fixed, smoothing, thrust, k4,workspace,false)
            for (i in values.indices) values[i] += (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]) * dt / 6.0
            }
            // The navigator compensates gravity and follows the same spline drawn in the preview.
            // Apply each physics substep so collision sweeps follow the curve, not its end-to-end chord.
            if (hasRoutes) bodies.forEachIndexed { i, body ->
                val path = body.routePath?.takeIf { body.fuelRemaining > 1e-9 } ?: return@forEachIndexed
                val elapsed = (step+1)*dt
                val powered = minOf(elapsed, body.fuelRemaining/fuelRate(body,controlledId))
                val sample = path.sample(body.routeDistance+body.routeSpeed*powered)
                val point = sample.position+sample.direction*(body.routeSpeed*(elapsed-powered))
                values[i*4]=point.x; values[i*4+1]=point.y
                values[i*4+2]=sample.direction.x*body.routeSpeed; values[i*4+3]=sample.direction.y*body.routeSpeed
                headings[i*2]=sample.direction.x; headings[i*2+1]=sample.direction.y
            }
        }
        return bodies.mapIndexed { i, body ->
            val point = Vec2(values[i * 4], values[i * 4 + 1])
            val powered = minOf(seconds, body.fuelRemaining/fuelRate(body,controlledId))
            val fuel = if (body.isVehicle) (body.fuelRemaining-seconds*fuelRate(body,controlledId)).coerceAtLeast(0.0) else body.fuelRemaining
            body.copy(position = point, velocity = Vec2(values[i * 4 + 2], values[i * 4 + 3]),
                heading = Vec2(headings[i * 2], headings[i * 2 + 1]),
                burnRemaining = (body.burnRemaining - seconds).coerceAtLeast(0.0),
                fuelRemaining = fuel,
                driftRemaining = if (body.kind == BodyKind.Rocket) (body.driftRemaining-(seconds-powered)).coerceAtLeast(0.0) else body.driftRemaining,
                routeDistance = if (body.routePath != null && fuel > 1e-9)
                    body.routePath.normalizeDistance(body.routeDistance+body.routeSpeed*powered) else 0.0,
                routePath = body.routePath.takeIf { fuel > 1e-9 },
                waypoints = if (body.isVehicle && fuel <= 1e-9) emptyList() else body.waypoints,
                trail = if (recordTrail) (body.trail.takeLast(41) + point) else body.trail)
        }
    }

    private fun fuelRate(body: CelestialBody, controlledId: Long?) =
        if (body.id == controlledId && body.pilotTargetSpeed != null) .12+1.38*body.pilotThrottle else
            1.0 + if (body.id == controlledId) .5*body.pilotThrottle else 0.0

    private fun derivative(state: DoubleArray, masses: DoubleArray, fixed: BooleanArray, smoothing: DoubleArray, thrust: DoubleArray, result: DoubleArray,
        workspace: Buffers,useTree: Boolean) {
        result.fill(0.0)
        if (useTree) {
            workspace.tree.compute(state,masses,smoothing,workspace.gravity,conserveMomentum=fixed.none { it })
            for (i in masses.indices) if (!fixed[i]) {
                result[i*4]=state[i*4+2]; result[i*4+1]=state[i*4+3]
                result[i*4+2]=workspace.gravity[i*2]+thrust[i*2]
                result[i*4+3]=workspace.gravity[i*2+1]+thrust[i*2+1]
            }
            return
        }
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
