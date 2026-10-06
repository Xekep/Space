package com.xekep.space.storage

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.xekep.space.sim.BodyKind
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import org.json.JSONArray
import org.json.JSONObject

data class SandboxSnapshot(
    val bodies: List<CelestialBody>,
    val cameraCenter: Vec2,
    val zoom: Float,
    val referenceEnergy: Double,
    val timestampUtcMillis: Long,
)

data class SandboxSlotSummary(
    val slot: Int,
    val bodyCount: Int,
    val timestampUtcMillis: Long,
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
                )
            }
        }
    }

    private fun encode(snapshot: SandboxSnapshot): String {
        val root = JSONObject()
        root.put("zoom", snapshot.zoom.toDouble())
        root.put("referenceEnergy", snapshot.referenceEnergy)
        root.put("timestampUtcMillis", snapshot.timestampUtcMillis)
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
                    .put("kind", body.kind.name),
            )
        }
        root.put("bodies", bodyArray)
        return root.toString()
    }

    private fun decode(raw: String): SandboxSnapshot {
        val root = JSONObject(raw)
        val camera = root.getJSONObject("cameraCenter")
        val bodiesJson = root.getJSONArray("bodies")
        val bodies = buildList {
            for (index in 0 until bodiesJson.length()) {
                val body = bodiesJson.getJSONObject(index)
                val position = Vec2(body.getDouble("x"), body.getDouble("y"))
                add(
                    CelestialBody(
                        id = body.getLong("id"),
                        position = position,
                        velocity = Vec2(body.getDouble("vx"), body.getDouble("vy")),
                        mass = body.getDouble("mass"),
                        radius = body.getDouble("radius").toFloat(),
                        color = Color(body.getInt("colorArgb")),
                        kind = BodyKind.valueOf(body.getString("kind")),
                        trail = listOf(position),
                    ),
                )
            }
        }
        return SandboxSnapshot(
            bodies = bodies,
            cameraCenter = Vec2(camera.getDouble("x"), camera.getDouble("y")),
            zoom = root.getDouble("zoom").toFloat(),
            referenceEnergy = root.getDouble("referenceEnergy"),
            timestampUtcMillis = root.getLong("timestampUtcMillis"),
        )
    }

    private fun key(slot: Int): String = "slot_$slot"
}
