package com.xekep.space.sim

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Vec2(
    val x: Double,
    val y: Double,
) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double): Vec2 = Vec2(x * scale, y * scale)
    operator fun div(scale: Double): Vec2 = Vec2(x / scale, y / scale)

    fun magnitude(): Double = sqrt((x * x) + (y * y))

    fun normalized(): Vec2 {
        val length = magnitude()
        return if (length <= 1e-9) Zero else this / length
    }

    fun perpendicular(): Vec2 = Vec2(-y, x)

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

enum class BodyKind {
    Core,
    Player,
    Meteor,
    Ambient,
}

data class CelestialBody(
    val id: Long,
    val position: Vec2,
    val velocity: Vec2,
    val mass: Double,
    val radius: Float,
    val color: Color,
    val kind: BodyKind = BodyKind.Ambient,
    val trail: List<Vec2> = listOf(position),
)

data class CollisionEvent(
    val firstKind: BodyKind,
    val secondKind: BodyKind,
    val position: Vec2,
)

data class StepResult(
    val bodies: List<CelestialBody>,
    val collisions: List<CollisionEvent>,
)

data class SandboxPreset(
    val bodies: List<CelestialBody>,
    val cameraCenter: Vec2,
    val zoom: Float,
    val referenceEnergy: Double,
)

private data class Derivative(
    val dPosition: Vec2,
    val dVelocity: Vec2,
)

object SimulationEngine {
    private const val gravitationalConstant = 400.0
    private const val softening = 18.0
    private const val maxSubstep = 1.0 / 120.0
    private const val sandboxSubstep = 1.0 / 240.0
    private const val maxLaunchSpeed = 260.0
    private const val launchVelocityScale = 1.1
    private const val trailLength = 42
    private const val meteorSpawnInset = 64.0
    private const val energyCorrectionBlend = 0.18
    private const val largeDistanceThreshold = 600.0

    private val idSource = AtomicLong(1L)
    private val palette = listOf(
        Color(0xFFF9C74F),
        Color(0xFF8BD3FF),
        Color(0xFFFF8A5B),
        Color(0xFFB8F2B3),
        Color(0xFFE6B8FF),
        Color(0xFFFFE29A),
    )

    fun demoBodies(viewport: Vec2): List<CelestialBody> {
        val center = Vec2(viewport.x / 2.0, viewport.y / 2.0)
        val sunMass = 6200.0
        val sun = body(center, Vec2.Zero, sunMass, Color(0xFFFFD166), BodyKind.Core)

        val innerRadius = min(viewport.x, viewport.y) * 0.22
        val innerSpeed = circularOrbitVelocity(sunMass, innerRadius) * 0.98
        val innerPlanet = body(
            center + Vec2(innerRadius, 0.0),
            Vec2(0.0, -innerSpeed),
            160.0,
            Color(0xFF8BD3FF),
            BodyKind.Ambient,
        )

        val outerRadius = min(viewport.x, viewport.y) * 0.34
        val outerSpeed = circularOrbitVelocity(sunMass, outerRadius) * 0.96
        val outerPlanetVelocity = Vec2(0.0, outerSpeed)
        val outerPlanetPosition = center + Vec2(-outerRadius, 0.0)
        val outerPlanet = body(
            outerPlanetPosition,
            outerPlanetVelocity,
            280.0,
            Color(0xFFFF8A5B),
            BodyKind.Ambient,
        )

        val moonRadius = 42.0
        val moonSpeed = circularOrbitVelocity(outerPlanet.mass, moonRadius) * 0.92
        val moon = body(
            outerPlanetPosition + Vec2(0.0, moonRadius),
            outerPlanetVelocity + Vec2(-moonSpeed, 0.0),
            24.0,
            Color(0xFFE6B8FF),
            BodyKind.Ambient,
        )

        return listOf(sun, innerPlanet, outerPlanet, moon)
    }

    fun sandboxPreset(): SandboxPreset {
        val sunMass = 12_000.0
        val sun = body(Vec2.Zero, Vec2.Zero, sunMass, Color(0xFFFFD166), BodyKind.Core)

        val planets = listOf(
            orbitalBody(57.9, 20.0, sunMass, 2.5, Color(0xFFD9B08C)),
            orbitalBody(108.2, 75.0, sunMass, 5.5, Color(0xFFF4C06A)),
            orbitalBody(149.6, 145.0, sunMass, 6.0, Color(0xFF69B7FF)),
            orbitalBody(227.9, 210.0, sunMass, 3.5, Color(0xFFFF8A5B)),
            orbitalBody(778.5, 310.0, sunMass, 55.0, Color(0xFFE7C89A)),
            orbitalBody(1433.5, 15.0, sunMass, 42.0, Color(0xFFF1D58A)),
            orbitalBody(2872.5, 96.0, sunMass, 26.0, Color(0xFF9BE7FF)),
            orbitalBody(4495.1, 262.0, sunMass, 28.0, Color(0xFF577CFF)),
        )

        val bodies = buildList {
            add(sun)
            addAll(planets)
        }
        return SandboxPreset(
            bodies = bodies,
            cameraCenter = Vec2.Zero,
            zoom = 0.11f,
            referenceEnergy = totalEnergy(bodies),
        )
    }

    fun arcadeBodies(viewport: Vec2): List<CelestialBody> {
        val center = Vec2(viewport.x / 2.0, viewport.y / 2.0)
        val core = body(center, Vec2.Zero, 7800.0, Color(0xFFFFD166), BodyKind.Core)
        val shieldRadius = min(viewport.x, viewport.y) * 0.17
        val shieldSpeed = circularOrbitVelocity(core.mass, shieldRadius) * 0.92
        val leftShield = body(
            center + Vec2(-shieldRadius, 0.0),
            Vec2(0.0, shieldSpeed),
            170.0,
            Color(0xFF8BD3FF),
            BodyKind.Ambient,
        )
        val rightShield = body(
            center + Vec2(shieldRadius, 0.0),
            Vec2(0.0, -shieldSpeed),
            170.0,
            Color(0xFFB8F2B3),
            BodyKind.Ambient,
        )
        return listOf(core, leftShield, rightShield)
    }

    fun createBody(position: Offset, dragEnd: Offset, holdSeconds: Double): CelestialBody {
        return createBody(position.toVec2(), dragEnd.toVec2(), holdSeconds, BodyKind.Player)
    }

    fun createBody(
        position: Vec2,
        dragEnd: Vec2,
        holdSeconds: Double,
        kind: BodyKind = BodyKind.Player,
    ): CelestialBody {
        val mass = massFromHold(holdSeconds)
        val velocity = launchVelocityFromDrag(position.toOffset(), dragEnd.toOffset())
        val id = idSource.getAndIncrement()
        val paletteIndex = ((id + mass.roundToLong()) % palette.size).toInt()
        return CelestialBody(
            id = id,
            position = position,
            velocity = velocity,
            mass = mass,
            radius = radiusForMass(mass),
            color = palette[paletteIndex],
            kind = kind,
        )
    }

    fun spawnMeteor(viewport: Vec2, difficulty: Double, random: Random): CelestialBody {
        val side = random.nextInt(4)
        val position = when (side) {
            0 -> Vec2(random.nextDouble(0.0, viewport.x), -meteorSpawnInset)
            1 -> Vec2(viewport.x + meteorSpawnInset, random.nextDouble(0.0, viewport.y))
            2 -> Vec2(random.nextDouble(0.0, viewport.x), viewport.y + meteorSpawnInset)
            else -> Vec2(-meteorSpawnInset, random.nextDouble(0.0, viewport.y))
        }

        val target = Vec2(
            x = (viewport.x * 0.5) + random.nextDouble(-viewport.x * 0.12, viewport.x * 0.12),
            y = (viewport.y * 0.5) + random.nextDouble(-viewport.y * 0.12, viewport.y * 0.12),
        )
        val direction = (target - position).normalized()
        val tangent = direction.perpendicular() * random.nextDouble(-0.35, 0.35)
        val velocity = (direction + tangent).normalized() * (90.0 + random.nextDouble(45.0) + difficulty * 7.0)
        val mass = 60.0 + random.nextDouble(110.0 + (difficulty * 18.0))
        return CelestialBody(
            id = idSource.getAndIncrement(),
            position = position,
            velocity = velocity,
            mass = mass,
            radius = radiusForMass(mass),
            color = Color(0xFFFF8A5B),
            kind = BodyKind.Meteor,
        )
    }

    fun step(bodies: List<CelestialBody>, dt: Double): StepResult {
        return stepInternal(
            bodies = bodies,
            dt = dt,
            collisionsEnabled = true,
            energyReference = null,
            substepLimit = maxSubstep,
        )
    }

    fun stepSandbox(bodies: List<CelestialBody>, dt: Double, referenceEnergy: Double): StepResult {
        return stepInternal(
            bodies = bodies,
            dt = dt,
            collisionsEnabled = false,
            energyReference = referenceEnergy,
            substepLimit = sandboxSubstep,
        )
    }

    fun totalEnergy(bodies: List<CelestialBody>): Double {
        return kineticEnergy(bodies) + potentialEnergy(bodies)
    }

    private fun stepInternal(
        bodies: List<CelestialBody>,
        dt: Double,
        collisionsEnabled: Boolean,
        energyReference: Double?,
        substepLimit: Double,
    ): StepResult {
        if (bodies.isEmpty() || dt <= 0.0) {
            return StepResult(bodies = bodies, collisions = emptyList())
        }

        var remaining = dt
        var current = bodies
        val collisions = mutableListOf<CollisionEvent>()
        while (remaining > 1e-6) {
            val substep = min(remaining, substepLimit)
            current = integrate(current, substep)
            if (energyReference != null && hasLargeDistances(current)) {
                current = stabilizeEnergy(current, energyReference)
            }
            if (collisionsEnabled) {
                val mergeResult = mergeCollisions(current)
                current = mergeResult.bodies
                collisions += mergeResult.collisions
            }
            remaining -= substep
        }

        return StepResult(bodies = current, collisions = collisions)
    }

    private fun integrate(bodies: List<CelestialBody>, dt: Double): List<CelestialBody> {
        val positions = bodies.map { it.position }
        val velocities = bodies.map { it.velocity }
        val masses = bodies.map { it.mass }

        val k1 = evaluate(positions, velocities, masses)
        val k2 = evaluate(
            advance(positions, k1.map { it.dPosition }, dt * 0.5),
            advance(velocities, k1.map { it.dVelocity }, dt * 0.5),
            masses,
        )
        val k3 = evaluate(
            advance(positions, k2.map { it.dPosition }, dt * 0.5),
            advance(velocities, k2.map { it.dVelocity }, dt * 0.5),
            masses,
        )
        val k4 = evaluate(
            advance(positions, k3.map { it.dPosition }, dt),
            advance(velocities, k3.map { it.dVelocity }, dt),
            masses,
        )

        return bodies.mapIndexed { index, body ->
            val nextPosition = positions[index] + weightedSum(
                k1[index].dPosition,
                k2[index].dPosition,
                k3[index].dPosition,
                k4[index].dPosition,
                dt,
            )
            val nextVelocity = velocities[index] + weightedSum(
                k1[index].dVelocity,
                k2[index].dVelocity,
                k3[index].dVelocity,
                k4[index].dVelocity,
                dt,
            )

            body.copy(
                position = nextPosition,
                velocity = nextVelocity,
                trail = appendTrail(body.trail, nextPosition),
            )
        }
    }

    private fun evaluate(
        positions: List<Vec2>,
        velocities: List<Vec2>,
        masses: List<Double>,
    ): List<Derivative> {
        val accelerations = accelerations(positions, masses)
        return positions.indices.map { index ->
            Derivative(
                dPosition = velocities[index],
                dVelocity = accelerations[index],
            )
        }
    }

    private fun accelerations(positions: List<Vec2>, masses: List<Double>): List<Vec2> {
        return positions.indices.map { index ->
            var acceleration = Vec2.Zero
            val origin = positions[index]
            for (otherIndex in positions.indices) {
                if (otherIndex == index) {
                    continue
                }

                val delta = positions[otherIndex] - origin
                val distanceSquared = (delta.x * delta.x) + (delta.y * delta.y) + (softening * softening)
                val distance = sqrt(distanceSquared)
                val inverseDistanceCubed = 1.0 / (distanceSquared * distance)
                val strength = gravitationalConstant * masses[otherIndex] * inverseDistanceCubed
                acceleration += delta * strength
            }
            acceleration
        }
    }

    private fun mergeCollisions(bodies: List<CelestialBody>): StepResult {
        if (bodies.size < 2) {
            return StepResult(bodies = bodies, collisions = emptyList())
        }

        val merged = mutableListOf<CelestialBody>()
        val consumed = BooleanArray(bodies.size)
        val collisions = mutableListOf<CollisionEvent>()

        for (index in bodies.indices) {
            if (consumed[index]) {
                continue
            }

            var current = bodies[index]
            for (otherIndex in (index + 1) until bodies.size) {
                if (consumed[otherIndex]) {
                    continue
                }

                val other = bodies[otherIndex]
                val distance = (current.position - other.position).magnitude()
                if (distance > current.radius + other.radius) {
                    continue
                }

                collisions += CollisionEvent(
                    firstKind = current.kind,
                    secondKind = other.kind,
                    position = (current.position + other.position) / 2.0,
                )
                current = merge(current, other)
                consumed[otherIndex] = true
            }

            merged += current
        }

        return StepResult(bodies = merged, collisions = collisions)
    }

    private fun merge(first: CelestialBody, second: CelestialBody): CelestialBody {
        val totalMass = first.mass + second.mass
        val dominant = if (first.mass >= second.mass) first else second
        val nextPosition = ((first.position * first.mass) + (second.position * second.mass)) / totalMass
        val nextVelocity = ((first.velocity * first.mass) + (second.velocity * second.mass)) / totalMass
        val nextKind = mergedKind(first, second, dominant)
        val nextColor = when (nextKind) {
            BodyKind.Core -> Color(0xFFFFD166)
            BodyKind.Meteor -> Color(0xFFFF8A5B)
            BodyKind.Player -> dominant.color
            BodyKind.Ambient -> dominant.color
        }

        return dominant.copy(
            position = nextPosition,
            velocity = nextVelocity,
            mass = totalMass,
            radius = radiusForMass(totalMass),
            color = nextColor,
            kind = nextKind,
            trail = appendTrail(dominant.trail, nextPosition),
        )
    }

    private fun orbitalBody(
        radius: Double,
        angleDegrees: Double,
        centralMass: Double,
        mass: Double,
        color: Color,
    ): CelestialBody {
        val angle = angleDegrees * PI / 180.0
        val position = Vec2(cos(angle) * radius, sin(angle) * radius)
        val tangent = position.perpendicular().normalized()
        val speed = circularOrbitVelocity(centralMass, radius)
        return body(
            position = position,
            velocity = tangent * speed,
            mass = mass,
            color = color,
            kind = BodyKind.Ambient,
        )
    }

    private fun advance(values: List<Vec2>, delta: List<Vec2>, dt: Double): List<Vec2> {
        return values.indices.map { index -> values[index] + (delta[index] * dt) }
    }

    private fun weightedSum(k1: Vec2, k2: Vec2, k3: Vec2, k4: Vec2, dt: Double): Vec2 {
        return (k1 + (k2 * 2.0) + (k3 * 2.0) + k4) * (dt / 6.0)
    }

    private fun appendTrail(trail: List<Vec2>, point: Vec2): List<Vec2> {
        val nextTrail = trail + point
        return if (nextTrail.size > trailLength) {
            nextTrail.drop(nextTrail.size - trailLength)
        } else {
            nextTrail
        }
    }

    private fun body(
        position: Vec2,
        velocity: Vec2,
        mass: Double,
        color: Color,
        kind: BodyKind,
    ): CelestialBody {
        return CelestialBody(
            id = idSource.getAndIncrement(),
            position = position,
            velocity = velocity,
            mass = mass,
            radius = radiusForMass(mass),
            color = color,
            kind = kind,
        )
    }

    private fun mergedKind(first: CelestialBody, second: CelestialBody, dominant: CelestialBody): BodyKind {
        return when {
            first.kind == BodyKind.Core || second.kind == BodyKind.Core -> BodyKind.Core
            dominant.kind == BodyKind.Player -> BodyKind.Player
            dominant.kind == BodyKind.Meteor -> BodyKind.Meteor
            first.kind == BodyKind.Player || second.kind == BodyKind.Player -> BodyKind.Player
            first.kind == BodyKind.Meteor || second.kind == BodyKind.Meteor -> BodyKind.Meteor
            else -> BodyKind.Ambient
        }
    }

    private fun circularOrbitVelocity(centralMass: Double, radius: Double): Double {
        return sqrt((gravitationalConstant * centralMass) / radius)
    }

    private fun kineticEnergy(bodies: List<CelestialBody>): Double {
        return bodies.sumOf { body ->
            0.5 * body.mass * (body.velocity.x * body.velocity.x + body.velocity.y * body.velocity.y)
        }
    }

    private fun potentialEnergy(bodies: List<CelestialBody>): Double {
        var potential = 0.0
        for (i in bodies.indices) {
            for (j in (i + 1) until bodies.size) {
                val delta = bodies[j].position - bodies[i].position
                val distance = sqrt((delta.x * delta.x) + (delta.y * delta.y) + (softening * softening))
                potential -= gravitationalConstant * bodies[i].mass * bodies[j].mass / distance
            }
        }
        return potential
    }

    private fun hasLargeDistances(bodies: List<CelestialBody>): Boolean {
        return bodies.any { body -> body.position.magnitude() >= largeDistanceThreshold }
    }

    private fun stabilizeEnergy(bodies: List<CelestialBody>, targetEnergy: Double): List<CelestialBody> {
        val currentKinetic = kineticEnergy(bodies)
        if (currentKinetic <= 1e-9) {
            return bodies
        }

        val currentPotential = potentialEnergy(bodies)
        val desiredKinetic = targetEnergy - currentPotential
        if (desiredKinetic <= 1e-9) {
            return bodies
        }

        val rawScale = sqrt(desiredKinetic / currentKinetic)
        if (!rawScale.isFinite() || abs(rawScale - 1.0) < 1e-4) {
            return bodies
        }

        val scale = 1.0 + ((rawScale - 1.0) * energyCorrectionBlend)
        val totalMass = bodies.sumOf { it.mass }
        val barycenterVelocity = bodies
            .fold(Vec2.Zero) { acc, body -> acc + (body.velocity * body.mass) } / totalMass

        return bodies.map { body ->
            body.copy(
                velocity = barycenterVelocity + ((body.velocity - barycenterVelocity) * scale),
            )
        }
    }

    fun massFromHold(holdSeconds: Double): Double {
        return 70.0 + (900.0 * holdSeconds.coerceAtMost(4.0))
    }

    fun energyCostForMass(mass: Double): Double {
        return 10.0 + (mass * 0.028)
    }

    fun radiusForMass(mass: Double): Float {
        return (6.0 + (sqrt(mass) * 0.24)).toFloat()
    }

    fun launchVelocityFromDrag(start: Offset, end: Offset): Vec2 {
        val raw = Vec2(
            x = (end.x - start.x) * launchVelocityScale,
            y = (end.y - start.y) * launchVelocityScale,
        )
        val speed = raw.magnitude()
        return if (speed <= maxLaunchSpeed) {
            raw
        } else {
            raw * (maxLaunchSpeed / speed)
        }
    }
}

fun Vec2.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

fun Offset.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
