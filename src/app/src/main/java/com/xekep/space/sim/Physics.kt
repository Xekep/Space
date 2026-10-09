package com.xekep.space.sim

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val MAX_SANDBOX_BODIES = 2000

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
    Ship,
    Rocket,
    Star,
    BlackHole,
    ArcadePlanet,
    Convoy,
}

enum class ShipClass { Interceptor, Guardian }

data class CelestialBody(
    val id: Long,
    val position: Vec2,
    val velocity: Vec2,
    val mass: Double,
    val radius: Float,
    val color: Color,
    val kind: BodyKind = BodyKind.Ambient,
    val trail: List<Vec2> = listOf(position),
    val burnRemaining: Double = 0.0, // Legacy save field; fuelRemaining drives the engine and flame.
    val heading: Vec2 = Vec2(0.0, -1.0),
    val solar: SolarBody? = null,
    val physicalScale: Boolean = solar != null,
    val waypoints: List<Vec2> = emptyList(),
    val routeSpeed: Double = 0.0,
    val routeTolerance: Double = 0.0,
    val pilotThrottle: Double = 0.0,
    val fuelRemaining: Double = vehicleFuelCapacity(kind),
    val driftRemaining: Double = if (kind == BodyKind.Rocket) ROCKET_DRIFT_SECONDS else 0.0,
    val routePath: FlightPath? = null,
    val routeDistance: Double = 0.0,
    val isDebris: Boolean = false,
    val pilotTargetSpeed: Double? = null,
    val shipClass: ShipClass = ShipClass.Interceptor,
    val fuelConsumptionScale: Double = 1.0,
    val galaxyParticle: Boolean = false, // Coarse stellar population, not a close two-body star system.
    val galaxySystemId: Long? = null,
    val orbitParentId: Long? = null,
    val routeAvoiding: Boolean = false, // Transient arcade detour; retain the authored spline.
    val solarOrbitScale: Double = 1.0, // Older saved catalogue worlds retain their original distances.
    val orbitalDetail: OrbitalDetail? = null, // Negligible-mass ring grain or artificial satellite.
    val flightHeight: Double = 0.0,
    val verticalVelocity: Double = 0.0,
    val pitch: Double = 0.0, // Radians; positive climbs toward the viewer.
    val roll: Double = 0.0, // Radians; positive banks to the craft's right.
)

data class CollisionEvent(
    val firstKind: BodyKind,
    val secondKind: BodyKind,
    val position: Vec2,
    val meteorId: Long? = null,
    val defenderId: Long? = null,
    val vehicleExplosion: Boolean = false,
    val velocity: Vec2 = Vec2.Zero,
    val seed: Int = 0,
    val debrisImpact: Boolean = false,
    val collapseRadius: Float = 0f,
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

enum class SandboxPresetKind(val title: String, val description: String) {
    SolarSystem("Solar system", "Start near the star. Pinch out to explore eight planets."),
    BinaryStars("Binary stars", "Two stars orbit a shared center of gravity."),
    ClassicOrbits("Orbits", "A star and eight planets in a playful gravity scale."),
    RandomSystems("Galaxy", "A rotating stellar disk with a nucleus, arms and varied clusters."),
    SystemGalaxy("System galaxy", "A galaxy of stars, planets and moons in hierarchical orbits."),
    Empty("Empty space", "A blank universe. Build your own system."),
}

object SimulationEngine {
    internal const val gravitationalConstant = 400.0
    private const val softening = 18.0
    private const val maxSubstep = 1.0 / 120.0
    private const val sandboxSubstep = 1.0 / 240.0
    private const val maxLaunchSpeed = 260.0
    private const val launchVelocityScale = 1.1
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

    fun sandboxPreset(kind: SandboxPresetKind = SandboxPresetKind.SolarSystem): SandboxPreset {
        if (kind == SandboxPresetKind.SystemGalaxy) {
            val bodies=SystemGalaxy.create(Random.Default,::newBodyId)
            return SandboxPreset(bodies,Vec2.Zero,.007f,totalEnergy(bodies))
        }
        if (kind == SandboxPresetKind.RandomSystems) {
            val bodies=RandomSystems.create(Random.Default,::newBodyId)
            return SandboxPreset(bodies,Vec2.Zero,.09f,totalEnergy(bodies))
        }
        if (kind == SandboxPresetKind.Empty) {
            return SandboxPreset(emptyList(), Vec2.Zero, 1f, 0.0)
        }
        if (kind == SandboxPresetKind.BinaryStars) {
            val speed = sqrt(gravitationalConstant * 4000.0 / (4.0 * 180.0))
            val stars = listOf(
                body(Vec2(-180.0, 0.0), Vec2(0.0, -speed), 4000.0, Color(0xFFFFD166), BodyKind.Core),
                body(Vec2(180.0, 0.0), Vec2(0.0, speed), 4000.0, Color(0xFF8BD3FF), BodyKind.Core),
            )
            return SandboxPreset(stars, Vec2.Zero, 0.8f, totalEnergy(stars))
        }
        if (kind == SandboxPresetKind.ClassicOrbits) {
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
                zoom = 1f,
                referenceEnergy = totalEnergy(bodies),
            )
        }
        val bodies = SolarSystem.create { idSource.getAndIncrement() }
        return SandboxPreset(bodies, Vec2.Zero, .01f, totalEnergy(bodies))
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

    fun spawnMeteor(
        viewport: Vec2,
        difficulty: Double,
        random: Random,
        arenaCenter: Vec2 = viewport / 2.0,
        entrySide: Int? = null,
    ): CelestialBody {
        val origin = arenaCenter - viewport / 2.0
        val side = entrySide ?: random.nextInt(4)
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
            position = position + origin,
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

    /** Arcade contacts complete threats, while sandbox contacts retain mass merging. */
    fun stepArcade(bodies: List<CelestialBody>, dt: Double, controlledId: Long? = null): StepResult {
        var current = bodies
        var remaining = dt
        val events = mutableListOf<CollisionEvent>()
        while (remaining > 1e-9) {
            val step = min(remaining, maxSubstep)
            var moved = NumericIntegrator.advance(current, step, maxSubstep, fixedCore = true, alignRockets = false, controlledId=controlledId).toMutableList()
            val fuel = expireVehicles(moved)
            moved = fuel.bodies.toMutableList(); events += fuel.collisions
            val removed = mutableSetOf<Long>()
            val prior = current.associateBy { it.id }
            // Core contact takes precedence, so a single threat has exactly one outcome.
            val core = moved.firstOrNull { it.kind == BodyKind.Core }
            for (i in moved.indices) {
                val meteor = moved[i]
                if (meteor.kind != BodyKind.Meteor || meteor.id in removed) continue
                fun contact(target: CelestialBody) = bodyContact(prior.getValue(meteor.id), meteor, prior.getValue(target.id), target)
                val defenderIndex = if (core != null && contact(core) != null)
                    moved.indexOf(core)
                else moved.withIndex().filter { it.value.id !in removed && it.value.kind in
                    listOf(BodyKind.Player,BodyKind.Ambient,BodyKind.ArcadePlanet,BodyKind.Convoy) }
                    .mapNotNull { (index,body) -> contact(body)?.let { index to it.fraction } }.minByOrNull { it.second }?.first ?: -1
                if (defenderIndex < 0) continue
                val defender = moved[defenderIndex]
                removed += meteor.id
                events += CollisionEvent(meteor.kind, defender.kind, meteor.position, meteor.id, defender.id,
                    vehicleExplosion = defender.isVehicle, velocity = defender.velocity * .12, seed = defender.id.toInt())
                if (defender.isVehicle) removed += defender.id
                else if (defender.kind !in listOf(BodyKind.Core,BodyKind.ArcadePlanet,BodyKind.Convoy)) {
                    val mass = defender.mass - meteor.mass * 0.6
                    if (mass < 35.0) removed += defender.id
                    else moved[defenderIndex] = defender.copy(mass = mass, radius = radiusForMass(mass),
                        velocity = (defender.velocity * defender.mass + meteor.velocity * meteor.mass) / (defender.mass + meteor.mass))
                }
            }
            val survivors = moved.filter { it.id !in removed }
            val impacts = vehicleCollisions(survivors, survivors.map { prior.getValue(it.id) }, arcade = true)
            events += impacts.collisions
            current = impacts.bodies.filter { it.id !in removed && (it.kind == BodyKind.Core || core == null ||
                it.kind == BodyKind.Meteor || (it.position - core.position).magnitude() > it.radius + core.radius) }
            remaining -= step
        }
        return StepResult(current, events)
    }

    fun orbitVelocity(center: CelestialBody, point: Vec2, satelliteMass: Double = 0.0,
        physicalScale: Boolean = center.physicalScale): Vec2 {
        val delta = point - center.position
        val radius = delta.magnitude()
        if (radius <= 1e-9 || !radius.isFinite()) return center.velocity
        val smoothing = if (center.physicalScale || physicalScale) .01 else softening
        val speed = sqrt(gravitationalConstant * (center.gravityMass + satelliteMass.coerceAtLeast(0.0)) * radius * radius /
            (radius * radius + smoothing * smoothing).pow(1.5))
        return center.velocity + delta.perpendicular().normalized() * speed
    }

    fun predictPath(body: CelestialBody, attractors: List<CelestialBody>): List<Vec2> {
        var point = body.position
        var velocity = body.velocity
        return List(24) { index ->
            var acceleration = Vec2.Zero
            attractors.forEach { other ->
                if (other.id == body.id) return@forEach
                val delta = other.position - point
                val smoothing = forceSoftening(body, other)
                val square = delta.x * delta.x + delta.y * delta.y + smoothing * smoothing
                acceleration += delta * (gravitationalConstant * other.gravityMass / (square * sqrt(square)))
            }
            if (body.kind == BodyKind.Rocket && body.enginePowered) {
                val burnRate=body.fuelConsumptionScale*body.vehicleFuelBurnScale
                acceleration += body.heading * (80.0 * body.vehicleAccelerationScale *
                    ((body.fuelRemaining - index * 0.06 * burnRate) / (.06 * burnRate)).coerceIn(0.0, 1.0))
            }
            velocity += acceleration * 0.06
            point += velocity * 0.06
            point
        }
    }

    fun stepSandbox(
        bodies: List<CelestialBody>,
        dt: Double,
        referenceEnergy: Double,
        collisionsEnabled: Boolean = false,
        controlledId: Long? = null,
        collisionMode: SandboxCollisionMode = SandboxCollisionMode.Merge,
    ): StepResult {
        if (bodies.any { it.orbitalDetail != null }) {
            val main = bodies.filter { it.orbitalDetail == null }
            val result = stepSandbox(main, dt, referenceEnergy, collisionsEnabled, controlledId, collisionMode)
            return OrbitalDetails.advance(bodies, result, dt)
        }
        return stepInternal(
            bodies = bodies,
            dt = dt,
            collisionsEnabled = collisionsEnabled,
            // A powered scene is an open energy system; correcting to its launch energy cancels thrust.
            energyReference = if (collisionsEnabled || bodies.size >= BARNES_HUT_THRESHOLD || bodies.any { it.isVehicle || it.kind == BodyKind.BlackHole || it.physicalScale }) null else referenceEnergy,
            substepLimit = if (bodies.size >= BARNES_HUT_THRESHOLD) sandboxStepLimit(bodies) else if (bodies.any { it.physicalScale }) min(
                if (collisionsEnabled || bodies.size < 40) sandboxSubstep else 1.0/30.0,sandboxStepLimit(bodies))
                else if (collisionsEnabled || bodies.size < 40) sandboxSubstep else sandboxStepLimit(bodies),
            controlledId = controlledId,
            collisionMode = collisionMode,
        )
    }

    /** Bound RK4 steps by local gravitational timescales and relative travel, keeping the
     * original minimum precision during close encounters. All pair forces remain exact. */
    internal fun sandboxStepLimit(bodies: List<CelestialBody>): Double {
        if (bodies.size >= BARNES_HUT_THRESHOLD) return BarnesHutGravity.stepLimit(bodies)
        val physical = bodies.any { it.physicalScale }
        val fraction = if (physical) .2 else .05
        val rates = DoubleArray(bodies.size)
        var travelLimit = 1.0 / 30.0
        for (i in bodies.indices) {
            val first = bodies[i]
            for (j in i + 1 until bodies.size) {
                val second = bodies[j]
                val dx = second.position.x - first.position.x; val dy = second.position.y - first.position.y
                val smoothing = forceSoftening(first, second)
                val square = dx * dx + dy * dy + smoothing * smoothing
                val distance = sqrt(square)
                val rate = gravitationalConstant / (square * distance)
                rates[i] += rate * second.gravityMass; rates[j] += rate * first.gravityMass
                val vx = second.velocity.x - first.velocity.x; val vy = second.velocity.y - first.velocity.y
                val speedSquared = vx * vx + vy * vy
                if (speedSquared > 1e-9) travelLimit = min(travelLimit, (if (physical) .2 else .1) * distance / sqrt(speedSquared))
            }
        }
        val gravityLimit = fraction / sqrt(rates.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12)
        return min(travelLimit, gravityLimit).coerceIn(if (physical) 1.0/20000 else sandboxSubstep, 1.0 / 30.0)
    }

    fun totalEnergy(bodies: List<CelestialBody>): Double {
        return kineticEnergy(bodies) + potentialEnergy(bodies)
    }

    fun reserveBodyIds(bodies: List<CelestialBody>) {
        val nextId = (bodies.maxOfOrNull { it.id } ?: 0L) + 1L
        idSource.updateAndGet { maxOf(it, nextId) }
    }
    fun newBodyId(): Long = idSource.getAndIncrement()

    private fun stepInternal(
        bodies: List<CelestialBody>,
        dt: Double,
        collisionsEnabled: Boolean,
        energyReference: Double?,
        substepLimit: Double,
        controlledId: Long? = null,
        collisionMode: SandboxCollisionMode = SandboxCollisionMode.Merge,
    ): StepResult {
        if (bodies.isEmpty() || dt <= 0.0) {
            return StepResult(bodies = bodies, collisions = emptyList())
        }

        if (!collisionsEnabled && bodies.none { it.isVehicle || it.kind == BodyKind.BlackHole }) {
            var next = NumericIntegrator.advance(bodies, dt, substepLimit, controlledId = controlledId)
            if (energyReference != null && hasLargeDistances(next)) next = stabilizeEnergy(next, energyReference)
            return StepResult(next, emptyList())
        }

        var remaining = dt
        var current = bodies
        val collisions = mutableListOf<CollisionEvent>()
        val ejecta=EjectaBudget(if (bodies.size >= BARNES_HUT_THRESHOLD) 16 else if (bodies.size >= 80) 32 else 1000)
        while (remaining > 1e-6) {
            val substep = min(remaining, substepLimit)
            val previous = current
            current = NumericIntegrator.advance(current, substep, substepLimit, recordTrail = false, controlledId = controlledId)
            val fuel = expireVehicles(current)
            current = fuel.bodies; collisions += fuel.collisions
            if (energyReference != null && hasLargeDistances(current)) {
                current = stabilizeEnergy(current, energyReference)
            }
            val absorption = absorbBlackHoles(previous,current)
            current = absorption.bodies
            collisions += absorption.collisions
            val impacts = vehicleCollisions(current, previous)
            current = impacts.bodies
            collisions += impacts.collisions
            if (collisionsEnabled) {
                val mergeResult = if (collisionMode == SandboxCollisionMode.Debris)
                    debrisCollisions(current,previous,substep,ejecta,idSource::getAndIncrement) else mergeCollisions(current, previous)
                val collapse = collapseMassiveRemnants(mergeResult.bodies, current)
                current = collapse.bodies
                collisions += mergeResult.collisions
                collisions += collapse.collisions
            }
            remaining -= substep
        }

        // Collision substeps must not allocate a 42-point trail for every intermediate state.
        return StepResult(bodies = current.map { it.copy(trail = appendMotionTrail(it,it.position,current.size >= BARNES_HUT_THRESHOLD)) }, collisions = collisions)
    }

    /** Sweep relative motion so fast vehicles cannot tunnel through small targets.
     * Impacts destroy vehicles; the celestial target remains a gravitational body. */
    private fun vehicleCollisions(bodies: List<CelestialBody>, previous: List<CelestialBody>, arcade: Boolean = false): StepResult {
        if (bodies.none { it.isVehicle }) return StepResult(bodies,emptyList())
        val before=previous.associateBy { it.id }
        val removed = mutableSetOf<Long>()
        val events = mutableListOf<CollisionEvent>()
        val candidates = mutableListOf<Triple<Int, Int, BodyContact>>()
        fun consider(i: Int,j: Int) {
            val first=bodies[i]; val second=bodies[j]
            val contact=bodyContact(before[first.id] ?: first,first,before[second.id] ?: second,second) ?: return
            candidates+=Triple(i,j,contact)
        }
        if (bodies.size >= BARNES_HUT_THRESHOLD) {
            for ((i,j) in collisionPairs(bodies,previous,before)) {
                if (bodies[i].isVehicle) consider(i,j)
                else if (bodies[j].isVehicle) consider(j,i)
            }
        } else for (i in bodies.indices) {
            if (!bodies[i].isVehicle) continue
            for (j in bodies.indices) if (i != j && (!bodies[j].isVehicle || j > i)) consider(i,j)
        }
        for ((i, j, contact) in candidates.sortedBy { it.third.fraction }) {
                val first = bodies[i]; val second = bodies[j]
                if (first.id in removed || second.id in removed || (arcade && second.kind == BodyKind.Convoy)) continue
                if (arcade && second.kind == BodyKind.Meteor) {
                    if (second.id in removed) continue
                    removed += second.id
                }
                removed += first.id
                if (second.isVehicle) removed += second.id
                events += CollisionEvent(if (arcade && second.kind == BodyKind.Meteor) second.kind else first.kind,
                    if (arcade && second.kind == BodyKind.Meteor) first.kind else second.kind,
                    contact.position,
                    meteorId = if (arcade && second.kind == BodyKind.Meteor) second.id else null,
                    defenderId = if (arcade && second.kind == BodyKind.Meteor) first.id else null,
                    vehicleExplosion = true, velocity = (first.velocity + second.velocity) * .12,
                    seed = (first.id xor second.id).toInt())
        }
        return StepResult(bodies.filter { it.id !in removed }, events)
    }

    private fun mergeCollisions(bodies: List<CelestialBody>, previous: List<CelestialBody>): StepResult {
        if (bodies.size < 2) {
            return StepResult(bodies = bodies, collisions = emptyList())
        }

        val before = previous.associateBy { it.id }
        val neighbours=if (bodies.size >= BARNES_HUT_THRESHOLD) {
            Array(bodies.size) { ArrayList<Int>() }.also { lists -> collisionPairs(bodies,previous,before,margin=1.4).forEach { (i,j) -> lists[i].add(j) } }
        } else null
        val merged = mutableListOf<CelestialBody>()
        val consumed = BooleanArray(bodies.size)
        val collisions = mutableListOf<CollisionEvent>()

        for (index in bodies.indices) {
            if (consumed[index]) {
                continue
            }

            var current = bodies[index]
            val candidates: Iterable<Int> = neighbours?.get(index)?.sorted() ?: ((index + 1) until bodies.size)
            for (otherIndex in candidates) {
                if (consumed[otherIndex]) {
                    continue
                }

                val other = bodies[otherIndex]
                val contact = bodyContact(before[current.id] ?: current, current, before[other.id] ?: other, other) ?: continue

                collisions += CollisionEvent(
                    firstKind = current.kind,
                    secondKind = other.kind,
                    position = contact.position,
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
        val dominant = if (first.kind == BodyKind.BlackHole) first else if (second.kind == BodyKind.BlackHole) second else if (first.mass >= second.mass) first else second
        val nextPosition = ((first.position * first.mass) + (second.position * second.mass)) / totalMass
        val nextVelocity = ((first.velocity * first.mass) + (second.velocity * second.mass)) / totalMass
        val nextKind = mergedKind(first, second, dominant)
        val nextColor = when (nextKind) {
            BodyKind.Core, BodyKind.Star -> Color(0xFFFFD166)
            BodyKind.BlackHole -> Color(0xFFCB9BFF)
            BodyKind.Meteor -> Color(0xFFFF8A5B)
            BodyKind.Player -> dominant.color
            BodyKind.Ambient, BodyKind.ArcadePlanet, BodyKind.Convoy -> dominant.color
            BodyKind.Ship, BodyKind.Rocket -> dominant.color
        }

        return dominant.copy(
            position = nextPosition,
            velocity = nextVelocity,
            mass = totalMass,
            radius = if (nextKind == BodyKind.BlackHole) (dominant.radius*kotlin.math.cbrt(totalMass/dominant.mass)).toFloat()
                else if (first.physicalScale && second.physicalScale)
                kotlin.math.cbrt(first.radius.toDouble().pow(3) + second.radius.toDouble().pow(3)).toFloat()
                else radiusForMass(totalMass),
            color = nextColor,
            kind = nextKind,
            burnRemaining = 0.0,
            solar = null,
            trail = listOf(nextPosition),
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
            first.kind == BodyKind.BlackHole || second.kind == BodyKind.BlackHole -> BodyKind.BlackHole
            first.kind == BodyKind.Star || second.kind == BodyKind.Star -> BodyKind.Star
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
                val smoothing = forceSoftening(bodies[i], bodies[j])
                val distance = sqrt((delta.x * delta.x) + (delta.y * delta.y) + (smoothing * smoothing))
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

    fun velocityFromGesture(deltaDp: Offset): Vec2 {
        val raw = Vec2(deltaDp.x * 3.0, deltaDp.y * 3.0)
        return if (raw.magnitude() > maxLaunchSpeed) raw.normalized() * maxLaunchSpeed else raw
    }
}

fun Vec2.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

fun Offset.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())

val CelestialBody.isVehicle: Boolean get() = kind == BodyKind.Ship || kind == BodyKind.Rocket

// Spacecraft act as test particles; their hull mass is used for launch cost, not planetary gravity.
val CelestialBody.gravityMass: Double get() = if (isVehicle || kind == BodyKind.Convoy) mass * 1e-9 else mass
