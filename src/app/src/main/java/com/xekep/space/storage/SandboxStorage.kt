package com.xekep.space.storage

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.Vec2
import com.xekep.space.sim.SolarBody
import org.json.JSONArray
import org.json.JSONObject

data class SandboxSnapshot(
    val bodies: List<CelestialBody>,
    val cameraCenter: Vec2,
    val zoom: Float,
    val referenceEnergy: Double,
    val timestampUtcMillis: Long,
    val timeScale: Double = 1.0,
    val paused: Boolean = false,
    val collisionsEnabled: Boolean = false,
    val preset: SandboxPresetKind = SandboxPresetKind.SolarSystem,
    val name: String = preset.title,
)

data class SandboxSlotSummary(
    val slot: Int,
    val bodyCount: Int,
    val timestampUtcMillis: Long,
    val name: String = "Universe",
    val preview: List<CelestialBody> = emptyList(),
)

class SandboxStorage(context: Context) {
    private val preferences = context.getSharedPreferences("space_sandbox_slots", Context.MODE_PRIVATE)

    fun load(slot: Int): SandboxSnapshot? {
        val raw = preferences.getString(key(slot), null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    fun save(slot: Int, snapshot: SandboxSnapshot) {
        preferences.edit().putString(key(slot), encode(snapshot)).apply()
    }

    fun summaries(): List<SandboxSlotSummary?> {
        return (1..3).map { slot ->
            load(slot)?.let { snapshot ->
                SandboxSlotSummary(
                    slot = slot,
                    bodyCount = snapshot.bodies.size,
                    timestampUtcMillis = snapshot.timestampUtcMillis,
                    name = snapshot.name,
                    preview = snapshot.bodies.take(64),
                )
            }
        }
    }

    fun encode(snapshot: SandboxSnapshot): String {
        val root = JSONObject()
        root.put("zoom", snapshot.zoom.toDouble())
        root.put("referenceEnergy", snapshot.referenceEnergy)
        root.put("timestampUtcMillis", snapshot.timestampUtcMillis)
        root.put("timeScale", snapshot.timeScale)
        root.put("paused", snapshot.paused)
        root.put("collisionsEnabled", snapshot.collisionsEnabled)
        root.put("preset", snapshot.preset.name)
        root.put("name", snapshot.name)
        root.put(
            "cameraCenter",
            JSONObject()
                .put("x", snapshot.cameraCenter.x)
                .put("y", snapshot.cameraCenter.y),
        )
        val bodyArray = JSONArray()
        snapshot.bodies.forEach { body ->
            bodyArray.put(
                JSONObject()
                    .put("id", body.id)
                    .put("x", body.position.x)
                    .put("y", body.position.y)
                    .put("vx", body.velocity.x)
                    .put("vy", body.velocity.y)
                    .put("mass", body.mass)
                    .put("radius", body.radius.toDouble())
                    .put("colorArgb", body.color.toArgb())
                    .put("kind", body.kind.name)
                    .put("solar", body.solar?.name ?: "")
                    .put("physicalScale", body.physicalScale)
                    .put("burnRemaining", body.burnRemaining)
                    .put("headingX", body.heading.x)
                    .put("headingY", body.heading.y)
                    .put("routeSpeed", body.routeSpeed)
                    .put("routeTolerance", body.routeTolerance)
                    .put("pilotThrottle", body.pilotThrottle)
                    .put("fuelRemaining", body.fuelRemaining)
                    .put("driftRemaining", body.driftRemaining)
                    .put("routeDistance", body.routeDistance)
                    .put("routeLoopStart", body.routePath?.loopStartIndex ?: -1)
                    .put("routeKnots", body.routePath?.let { path -> JSONArray().also { array ->
                        path.knots.forEach { point -> array.put(JSONObject().put("x",point.x).put("y",point.y)) }
                    } })
                    .put("waypoints", JSONArray().also { array -> body.waypoints.forEach { point -> array.put(JSONObject().put("x",point.x).put("y",point.y)) } }),
            )
        }
        root.put("bodies", bodyArray)
        return root.toString()
    }

    fun decode(raw: String): SandboxSnapshot {
        val root = JSONObject(raw)
        val camera = root.getJSONObject("cameraCenter")
        val bodiesJson = root.getJSONArray("bodies")
        require(bodiesJson.length() <= 1000) { "Scene contains too many bodies" }
        val bodies = buildList {
            for (index in 0 until bodiesJson.length()) {
                val body = bodiesJson.getJSONObject(index)
                val position = Vec2(body.getDouble("x"), body.getDouble("y"))
                val velocity = Vec2(body.getDouble("vx"), body.getDouble("vy"))
                val id = body.getLong("id")
                require(id in 1..Long.MAX_VALUE - 1001)
                require(position.x in -1e9..1e9 && position.y in -1e9..1e9 && body.getDouble("mass") in 1e-8..100000000.0)
                require(velocity.magnitude() <= 5000.0 && body.getDouble("radius") in 1e-6..10000.0)
                val burn = body.optDouble("burnRemaining", 0.0)
                val heading = Vec2(body.optDouble("headingX", 0.0), body.optDouble("headingY", -1.0))
                require(burn in 0.0..3.0 && heading.x.isFinite() && heading.y.isFinite() && kotlin.math.abs(heading.magnitude() - 1.0) < 1e-6)
                val route=body.optJSONArray("waypoints") ?: JSONArray()
                require(route.length() <= com.xekep.space.sim.MAX_WAYPOINTS)
                val points=List(route.length()) { pointIndex ->
                    val point=route.getJSONObject(pointIndex)
                    val value=Vec2(point.getDouble("x"),point.getDouble("y"))
                    require(value.x in -1e9..1e9 && value.y in -1e9..1e9)
                    value
                }
                val routeSpeed=body.optDouble("routeSpeed",0.0)
                require(routeSpeed in 0.0..450.0)
                val routeTolerance=body.optDouble("routeTolerance",0.0)
                require(routeTolerance in 0.0..1000000.0)
                val pilotThrottle=body.optDouble("pilotThrottle",0.0)
                require(pilotThrottle in 0.0..1.0)
                val kind=BodyKind.valueOf(body.getString("kind"))
                val fuel=body.optDouble("fuelRemaining",com.xekep.space.sim.vehicleFuelCapacity(kind))
                require(fuel in 0.0..com.xekep.space.sim.vehicleFuelCapacity(kind))
                val drift=body.optDouble("driftRemaining",if (kind == BodyKind.Rocket) com.xekep.space.sim.ROCKET_DRIFT_SECONDS else 0.0)
                require(drift in 0.0..com.xekep.space.sim.ROCKET_DRIFT_SECONDS)
                val path=body.optJSONArray("routeKnots")?.let { knots ->
                    require(knots.length() in 2..com.xekep.space.sim.MAX_WAYPOINTS+1)
                    val values=List(knots.length()) { knotIndex ->
                        val knot=knots.getJSONObject(knotIndex)
                        Vec2(knot.getDouble("x"),knot.getDouble("y")).also {
                            require(it.x in -1e9..1e9 && it.y in -1e9..1e9)
                        }
                    }
                    require(values.zipWithNext().all { (a,b) -> (b-a).magnitude() > 1e-8 })
                    val loopStart=body.optInt("routeLoopStart",-1)
                    require(loopStart in -1..1)
                    com.xekep.space.sim.FlightPath(values,loopStart.takeIf { it >= 0 })
                }
                val routeDistance=body.optDouble("routeDistance",0.0)
                require(routeDistance in 0.0..(path?.length ?: 0.0))
                if (path != null) {
                    require(kind == BodyKind.Ship || kind == BodyKind.Rocket)
                    require(path.remainingPoints(routeDistance) == points && routeSpeed > 0.0)
                }
                add(
                    CelestialBody(
                        id = id,
                        position = position,
                        velocity = velocity,
                        mass = body.getDouble("mass"),
                        radius = body.getDouble("radius").toFloat(),
                        color = Color(body.getInt("colorArgb")),
                        kind = kind,
                        trail = listOf(position),
                        burnRemaining = burn,
                        heading = heading,
                        waypoints = points, routeSpeed = routeSpeed, routeTolerance = routeTolerance,
                        pilotThrottle = pilotThrottle,
                        fuelRemaining = fuel,
                        driftRemaining = drift, routePath = path, routeDistance = routeDistance,
                        physicalScale = body.optBoolean("physicalScale", body.optString("solar", "").isNotEmpty()),
                        solar = body.optString("solar", "").takeIf { it.isNotEmpty() }?.let(SolarBody::valueOf),
                    ),
                )
            }
        }
        require(bodies.map { it.id }.distinct().size == bodies.size)
        require(camera.getDouble("x") in -1e9..1e9 && camera.getDouble("y") in -1e9..1e9 && root.getDouble("zoom") in 0.001..1000.0)
        require(root.getDouble("referenceEnergy").isFinite())
        return SandboxSnapshot(
            bodies = bodies,
            cameraCenter = Vec2(camera.getDouble("x"), camera.getDouble("y")),
            zoom = root.getDouble("zoom").toFloat(),
            referenceEnergy = root.getDouble("referenceEnergy"),
            timestampUtcMillis = root.getLong("timestampUtcMillis"),
            timeScale = root.optDouble("timeScale", 1.0).takeIf { it in listOf(0.25, 1.0, 3.0, 6.0) } ?: 1.0,
            paused = root.optBoolean("paused", false),
            collisionsEnabled = root.optBoolean("collisionsEnabled", false),
            preset = runCatching {
                SandboxPresetKind.valueOf(root.optString("preset", SandboxPresetKind.SolarSystem.name))
            }.getOrDefault(SandboxPresetKind.SolarSystem),
            name = root.optString("name", "Universe").take(40),
        )
    }

    private fun key(slot: Int): String = "slot_$slot"
}
