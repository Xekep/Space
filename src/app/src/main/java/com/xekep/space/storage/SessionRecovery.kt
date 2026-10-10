package com.xekep.space.storage

import android.content.Context
import android.util.AtomicFile
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import com.xekep.space.ui.space.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Immutable capture made on the UI thread; disk/JSON work belongs on Dispatchers.IO. */
data class RecoverySnapshot(
    val mode: AppMode, val arcade: ArcadeSession?, val sandbox: SandboxSnapshot?,
    val dirty: Boolean, val spawnKind: BodyKind, val shipClass: ShipClass,
    val tutorialStep: Int, val presentationAge: Double, val hiddenOrbits: Set<Long>,
    val randomState: Long?, val flightPractice: Boolean = false, val practiceJoystick: Boolean = false,
    val practiceHeading: Vec2 = Vec2(0.0,-1.0), val practiceSpeed: Double = 0.0,
)

class SessionRecovery(context: Context, file: File = File(context.filesDir,"session-recovery.json")) {
    private val file = AtomicFile(file)
    private val scenes = SandboxStorage(context)
    var readFailed: Boolean = false; private set
    fun load(): RecoverySnapshot? {
        if (!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return runCatching {
            val bytes = file.openRead().use { input ->
                val bytes=input.readNBytesBounded(MAX_SANDBOX_IMPORT_BYTES)
                bytes
            }
            decode(String(bytes,Charsets.UTF_8))
        }.onFailure { readFailed=true }.getOrNull()
    }
    fun save(snapshot: RecoverySnapshot) {
        val bytes=encode(snapshot).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_SANDBOX_IMPORT_BYTES)
        val output=file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }
    fun encode(snapshot: RecoverySnapshot): String {
        val payload=JSONObject().put("mode",snapshot.mode.name).put("dirty",snapshot.dirty)
            .put("spawnKind",snapshot.spawnKind.name).put("shipClass",snapshot.shipClass.name)
            .put("tutorialStep",snapshot.tutorialStep).put("presentationAge",snapshot.presentationAge)
            .put("hiddenOrbits",JSONArray(snapshot.hiddenOrbits.toList())).put("randomState",snapshot.randomState)
            .put("flightPractice",snapshot.flightPractice).put("practiceJoystick",snapshot.practiceJoystick)
            .put("practiceHeading",vec(snapshot.practiceHeading)).put("practiceSpeed",snapshot.practiceSpeed)
        snapshot.sandbox?.let { payload.put("sandbox",JSONObject(scenes.encode(it))) }
        snapshot.arcade?.let { payload.put("arcade",encodeArcade(it)) }
        val raw=payload.toString()
        return JSONObject().put("version",1).put("payload",raw).put("sha256",hash(raw)).toString()
    }
    fun decode(raw: String): RecoverySnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_SANDBOX_IMPORT_BYTES)
        val envelope=JSONObject(raw); require(envelope.getInt("version") == 1)
        val content=envelope.getString("payload"); require(hash(content) == envelope.getString("sha256"))
        val root=JSONObject(content)
        val sandbox=root.optJSONObject("sandbox")?.let { scenes.decode(it.toString()) }
        val arcade=root.optJSONObject("arcade")?.let(::decodeArcade)
        val mode=AppMode.valueOf(root.getString("mode"))
        require(if (mode == AppMode.Arcade) arcade != null else sandbox != null)
        val step=root.getInt("tutorialStep"); require(step in -1..2)
        val age=root.getDouble("presentationAge"); require(age in 0.0..60.0)
        val hidden=root.getJSONArray("hiddenOrbits").longSet()
        val random=if (root.isNull("randomState")) null else root.getLong("randomState").also { require(it != 0L) }
        return RecoverySnapshot(mode,arcade,sandbox,root.getBoolean("dirty"),BodyKind.valueOf(root.getString("spawnKind")),
            ShipClass.valueOf(root.getString("shipClass")),step,age,hidden,random,
            root.optBoolean("flightPractice"),root.optBoolean("practiceJoystick"),root.optJSONObject("practiceHeading")?.let(::readVec) ?: Vec2(0.0,-1.0),
            root.optDouble("practiceSpeed",0.0).also { require(it in 0.0..5000.0) })
    }
    private fun bodyScene(bodies: List<CelestialBody>, camera: SpaceCamera = SpaceCamera()): JSONObject =
        JSONObject(scenes.encode(SandboxSnapshot(bodies,camera.center,camera.zoom,0.0,0L,preset=SandboxPresetKind.Empty)))
    private fun encodeArcade(run: ArcadeSession): JSONObject {
        val result=JSONObject().put("scene",bodyScene(run.bodies,run.camera))
            .put("arenaWidth",run.arena.width).put("arenaHeight",run.arena.height)
            .put("pendingScene",bodyScene(run.pending.map { it.body }))
            .put("pendingSeconds",JSONArray(run.pending.map { it.seconds }))
            .put("difficulty",run.difficulty.name).put("energy",run.energy).put("score",run.score).put("lives",run.lives)
            .put("destroyed",run.destroyed).put("elapsed",run.elapsed).put("spawnTimer",run.spawnTimer).put("combo",run.combo)
            .put("immunity",run.immunity).put("lastIntercept",run.lastIntercept).put("launches",run.launches)
            .put("successfulLaunches",JSONArray(run.successfulLaunches.toList())).put("practice",run.practice)
            .put("waveDelay",run.waveDelay).put("planetId",run.planetId).put("endless",run.endless)
            .put("offered",JSONArray(run.offeredUpgradeWaves.toList())).put("chosen",JSONArray(run.chosenUpgradeWaves.toList()))
        val upgrades=JSONObject(); run.upgrades.forEach { (kind,level) -> upgrades.put(kind.name,level) }; result.put("upgrades",upgrades)
        run.upgradeOffer?.let { result.put("offer",JSONObject().put("wave",it.wave).put("choices",JSONArray(it.choices.map { x -> x.name })).put("bonus",it.convoyBonus).put("salvageBonus",it.salvageBonus)) }
        run.challenge?.let { result.put("challenge",JSONObject().put("parentId",it.parentId).put("ids",JSONArray(it.ids.toList())).put("failed",it.failed).put("rewarded",it.rewarded)) }
        run.convoy?.let { result.put("convoy",JSONObject().put("bodyId",it.bodyId).put("hull",it.hull).put("elapsed",it.elapsed).put("status",it.status.name).put("bonusAwarded",it.bonusAwarded)) }
        result.put("salvageCollected",run.salvageCollected)
        run.salvage?.let { result.put("salvage",JSONObject().put("wave",it.wave).put("position",vec(it.position))
            .put("remaining",it.remaining).put("status",it.status.name).put("rewarded",it.rewarded)) }
        run.carrier?.let { result.put("carrier",JSONObject().put("ids",JSONArray(it.nodeIds.toList()))
            .put("angle",it.angle).put("elapsed",it.elapsed).put("defeated",it.defeated)) }
        result.put("shots",JSONArray().also { array -> run.combat.projectiles.forEach { shot -> array.put(JSONObject()
            .put("position",vec(shot.position)).put("velocity",vec(shot.velocity)).put("owner",shot.ownerId).put("remaining",shot.remaining)
            .put("damage",shot.damage).put("height",shot.height).put("vertical",shot.verticalVelocity)) } })
        result.put("craft",JSONArray().also { array -> run.combat.craft.forEach { (id,status) -> array.put(JSONObject().put("id",id)
            .put("age",status.age).put("cooldown",status.cooldown).put("target",status.targetId)) } })
        return result
    }
    private fun decodeArcade(root: JSONObject): ArcadeSession {
        fun value(key: String,range: ClosedFloatingPointRange<Double>): Double = root.getDouble(key).also { require(it in range) }
        val scene=scenes.decode(root.getJSONObject("scene").toString())
        val pending=scenes.decode(root.getJSONObject("pendingScene").toString()).bodies
        require(scene.bodies.count { it.kind == BodyKind.Core } == 1)
        require((scene.bodies+pending).map { it.id }.distinct().size == scene.bodies.size+pending.size)
        val times=root.getJSONArray("pendingSeconds"); require(times.length() == pending.size && pending.size <= 18)
        val difficulty=ArcadeDifficulty.valueOf(root.getString("difficulty"))
        val levels=root.getJSONObject("upgrades"); val upgrades=levels.keys().asSequence().associate { key -> ArcadeUpgrade.valueOf(key) to levels.getInt(key).also { require(it in 0..2) } }
        val offer=root.optJSONObject("offer")?.let { obj -> val array=obj.getJSONArray("choices"); require(array.length() in 1..3)
            ArcadeUpgradeOffer(obj.getInt("wave").also { require(it > 0) },List(array.length()) { ArcadeUpgrade.valueOf(array.getString(it)) },obj.getBoolean("bonus"),obj.optBoolean("salvageBonus",false)) }
        val challenge=root.optJSONObject("challenge")?.let { ArcadeChallenge(it.getLong("parentId"),it.getJSONArray("ids").longSet(),it.getBoolean("failed"),it.getBoolean("rewarded")) }
        val convoy=root.optJSONObject("convoy")?.let { ArcadeConvoy(it.getLong("bodyId"),it.getInt("hull").also { x -> require(x in 0..3) },
            it.getDouble("elapsed").also { x -> require(x in 0.0..1e8) },ConvoyStatus.valueOf(it.getString("status")),it.getBoolean("bonusAwarded")) }
        val salvage=root.optJSONObject("salvage")?.let { ArcadeSalvage(it.getInt("wave").also { w -> require(w in listOf(7,13)) },
            readVec(it.getJSONObject("position")),it.getDouble("remaining").also { t -> require(t in 0.0..32.0) },
            SalvageStatus.valueOf(it.getString("status")),it.getBoolean("rewarded")) }
        val carrier=root.optJSONObject("carrier")?.let { ArcadeCarrier(it.getJSONArray("ids").longSet().also { ids -> require(ids.size == 3) },
            it.getDouble("angle").also { angle -> require(angle in 0.0..(2*kotlin.math.PI)) },
            it.getDouble("elapsed").also { t -> require(t in 0.0..101.0) },it.getBoolean("defeated")) }
        val shots=root.getJSONArray("shots"); require(shots.length() <= 1000)
        val projectiles=List(shots.length()) { index -> val obj=shots.getJSONObject(index)
            SpaceProjectile(readVec(obj.getJSONObject("position")),readVec(obj.getJSONObject("velocity")),obj.getLong("owner"),
                obj.getDouble("remaining").also { require(it in 0.0..5.0) },obj.getDouble("damage").also { require(it in 0.0..10000.0) },
                obj.getDouble("height").also { require(it in -1e6..1e6) },obj.getDouble("vertical").also { require(it in -5000.0..5000.0) }) }
        val statuses=root.getJSONArray("craft"); require(statuses.length() <= 1000)
        val craft=List(statuses.length()) { index -> val obj=statuses.getJSONObject(index)
            obj.getLong("id") to CraftStatus(obj.getDouble("age").also { require(it in 0.0..1e8) },obj.getDouble("cooldown").also { require(it in -5.0..5.0) },
                if (obj.isNull("target")) null else obj.getLong("target")) }.toMap()
        val arena=IntSize(root.getInt("arenaWidth"),root.getInt("arenaHeight"))
        require(arena.width in 1..16000 && arena.height in 1..16000)
        val run=ArcadeSession(scene.bodies,SpaceCamera(scene.cameraCenter,scene.zoom),arena,difficulty,
            energy=value("energy",0.0..160.0),score=value("score",0.0..1e15),lives=root.getInt("lives").also { require(it in 0..difficulty.lives) },
            destroyed=root.getInt("destroyed").also { require(it >= 0) },elapsed=value("elapsed",0.0..1e8),spawnTimer=value("spawnTimer",-10.0..100.0),
            combo=value("combo",1.0..100.0),immunity=value("immunity",0.0..10.0),lastIntercept=value("lastIntercept",0.0..1e8),
            pending=pending.mapIndexed { index,body -> PendingThreat(body,times.getDouble(index).also { require(it in 0.0..10.0) }) },
            launches=root.getInt("launches").also { require(it >= 0) },successfulLaunches=root.getJSONArray("successfulLaunches").longSet(),practice=root.getBoolean("practice"),
            combat=ArcadeCombat(projectiles,craft),upgrades=upgrades,offeredUpgradeWaves=root.getJSONArray("offered").intSet(),chosenUpgradeWaves=root.getJSONArray("chosen").intSet(),
            upgradeOffer=offer,challenge=challenge,waveDelay=value("waveDelay",0.0..1e8),planetId=if (root.isNull("planetId")) null else root.getLong("planetId"),convoy=convoy,endless=root.getBoolean("endless"),
            carrier=carrier,salvage=salvage,salvageCollected=root.optInt("salvageCollected",0).also { require(it in 0..2) })
        require(run.energy <= run.maxEnergy && run.waveDelay <= run.elapsed)
        carrier?.let { boss ->
            val nodes=run.bodies.filter { it.id in boss.nodeIds }; require(nodes.all { it.kind == BodyKind.Meteor })
            require(if (boss.defeated) nodes.isEmpty() else nodes.isNotEmpty())
        }
        return run
    }
    private fun vec(value: Vec2) = JSONObject().put("x",value.x).put("y",value.y)
    private fun readVec(obj: JSONObject) = Vec2(obj.getDouble("x"),obj.getDouble("y")).also { require(it.x in -1e9..1e9 && it.y in -1e9..1e9) }
    private fun JSONArray.longSet(): Set<Long> { require(length() <= 10000); return (0 until length()).map { getLong(it).also { value -> require(value in 1..Long.MAX_VALUE-2001) } }.toSet() }
    private fun JSONArray.intSet(): Set<Int> { require(length() <= 10000); return (0 until length()).map { getInt(it).also { value -> require(value > 0) } }.toSet() }
    private fun hash(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun java.io.InputStream.readNBytesBounded(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while (true) { val count=read(buffer); if (count < 0) break
            require(output.size()+count <= limit); output.write(buffer,0,count) }
        return output.toByteArray()
    }
}
