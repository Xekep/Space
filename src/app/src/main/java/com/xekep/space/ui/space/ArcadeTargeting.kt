package com.xekep.space.ui.space

import com.xekep.space.sim.*
import kotlin.math.*

internal data class GunSolution(val direction: Vec2, val velocity: Vec2, val origin: Vec2, val seconds: Double, val height: Double = 0.0, val verticalVelocity: Double = 0.0)

/** A short, shared forecast, built only when a gun is ready and a target is in range.
 * Projectiles are ballistic; enemy paths use the game's softened gravitational field.
 */
internal class ArcadeTargeting(scene: List<CelestialBody>) {
    private val duration = 1.3
    private val step = 1.0 / 30.0
    private val sources = scene.filter { !it.isVehicle && it.kind != BodyKind.Convoy }
    private val sourceAcceleration = sources.associate { source -> source.id to
        if (source.kind == BodyKind.Core) Vec2.Zero else gravity(source.position, source, 0.0, forecast = false) }
    private val tracks = mutableMapOf<Long, List<Vec2>>()

    private fun sourcePosition(body: CelestialBody, time: Double): Vec2 = if (body.kind == BodyKind.Core) body.position else
        body.position + body.velocity * time + (sourceAcceleration[body.id] ?: Vec2.Zero) * (.5 * time * time)

    private fun gravity(point: Vec2, target: CelestialBody, time: Double, forecast: Boolean = true): Vec2 {
        var acceleration = Vec2.Zero
        for (source in sources) if (source.id != target.id) {
            val delta = (if (forecast) sourcePosition(source, time) else source.position) - point
            val soft = forceSoftening(target, source)
            val square = delta.x * delta.x + delta.y * delta.y + soft * soft
            acceleration += delta * (SimulationEngine.gravitationalConstant * source.gravityMass / (square * sqrt(square)))
        }
        return acceleration
    }

    private fun track(target: CelestialBody): List<Vec2> = tracks.getOrPut(target.id) {
        var point = target.position; var velocity = target.velocity
        val points = mutableListOf(point)
        repeat(ceil(duration / step).toInt()) { index ->
            val time = index * step
            val a = gravity(point, target, time)
            val v2 = velocity + a * (step / 2)
            val b = gravity(point + velocity * (step / 2), target, time + step / 2)
            val v3 = velocity + b * (step / 2)
            val c = gravity(point + v2 * (step / 2), target, time + step / 2)
            val v4 = velocity + c * step
            val d = gravity(point + v3 * step, target, time + step)
            point += (velocity + v2 * 2.0 + v3 * 2.0 + v4) * (step / 6)
            velocity += (a + b * 2.0 + c * 2.0 + d) * (step / 6)
            points += point
        }
        points
    }

    private fun position(track: List<Vec2>, seconds: Double): Vec2 {
        val frame = (seconds / step).coerceIn(0.0, (track.size - 1).toDouble())
        val index = floor(frame).toInt().coerceAtMost(track.size - 2)
        return track[index] + (track[index + 1] - track[index]) * (frame - index)
    }

    fun solution(ship: CelestialBody, target: CelestialBody, shipVelocity: Vec2, speed: Double): GunSolution? {
        val path = track(target)
        val inherited = shipVelocity * .25
        val muzzle = ship.radius + 4.0
        fun distance(time: Double) = hypot((position(path, time) - ship.position - inherited * time).magnitude(),ship.flightHeight+ship.verticalVelocity*.25*time) - muzzle - speed * time
        var low = 0.0; var high = 0.0
        if (distance(0.0) > 0.0) {
            var found = false
            for (index in 1 until path.size) {
                high = minOf(duration, index * step)
                if (distance(high) <= 0.0) { found = true; break }
                low = high
            }
            if (!found) return null
            repeat(8) { val middle = (low + high) / 2; if (distance(middle) > 0) low = middle else high = middle }
        }
        val delta = position(path, high) - ship.position - inherited * high
        val dz = -ship.flightHeight-ship.verticalVelocity*.25*high
        val length = hypot(delta.magnitude(),dz)
        if (length < 1e-9) return null
        val direction = delta/length
        val verticalDirection = dz/length
        val origin = ship.position + direction * muzzle
        val velocity = direction * speed + inherited
        val solution = GunSolution(direction, velocity, origin, high, ship.flightHeight+verticalDirection*muzzle,verticalDirection*speed+ship.verticalVelocity*.25)
        // Predict the moving obstacle too; a visible target can still be hidden behind the planet.
        for (planet in sources.filter { it.kind == BodyKind.ArcadePlanet }) {
            val count = ceil(high / step).toInt().coerceAtLeast(1)
            for (index in 0 until count) {
                val start = high * index / count; val end = high * (index + 1) / count
                if (firstSphereContact(origin + velocity * start - sourcePosition(planet, start),
                    origin + velocity * end - sourcePosition(planet, end),solution.height+solution.verticalVelocity*start,solution.height+solution.verticalVelocity*end,planet.radius + 3.0) != null) return null
            }
        }
        return solution
    }
}
