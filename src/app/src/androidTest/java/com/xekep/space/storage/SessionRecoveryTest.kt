package com.xekep.space.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import com.xekep.space.ui.space.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import java.io.File
import java.util.UUID

class SessionRecoveryTest {
    private val target=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="recovery_test_"+UUID.randomUUID()
    private val context=object: ContextWrapper(target) {
        override fun getSharedPreferences(key: String,mode: Int): SharedPreferences=target.getSharedPreferences(name,mode)
    }
    private val disk=File(target.cacheDir,name+".json")
    private val storage=SessionRecovery(context,disk)
    @After fun cleanup() { disk.delete(); File(disk.path+".bak").delete(); File(disk.path+".new").delete(); target.deleteSharedPreferences(name) }
    private fun snapshot(): RecoverySnapshot {
        val core=SimulationEngine.arcadeBodies(Vec2(900.0,1400.0)).first()
        val path=FlightPath(listOf(Vec2(100.0,100.0),Vec2(160.0,100.0),Vec2(180.0,160.0)))
        val ship=CelestialBody(SimulationEngine.newBodyId(),Vec2(100.0,100.0),Vec2(25.0,5.0),60.0,20f,Color.Cyan,BodyKind.Ship,
            fuelRemaining=89.0,routePath=path,waypoints=path.remainingPoints(0.0),routeSpeed=25.0,routeAvoiding=true)
        val meteor=CelestialBody(SimulationEngine.newBodyId(),Vec2(1700.0,700.0),Vec2(-120.0,0.0),100.0,10f,Color.Red,BodyKind.Meteor)
        val convoy=CelestialBody(SimulationEngine.newBodyId(),Vec2(800.0,900.0),Vec2(0.0,-75.0),100.0,10f,Color.Green,BodyKind.Convoy)
        val run=ArcadeSession(listOf(core,ship,convoy),SpaceCamera(core.position,.7f),IntSize(1100,1800),ArcadeDifficulty.Normal,
            elapsed=400.0,lives=3,energy=140.0,score=12345.0,upgrades=mapOf(ArcadeUpgrade.Reactor to 1),
            offeredUpgradeWaves=setOf(3,6,10),chosenUpgradeWaves=setOf(3,6,10),
            upgradeOffer=ArcadeUpgradeOffer(15,listOf(ArcadeUpgrade.Guns,ArcadeUpgrade.Repair),true),
            challenge=ArcadeChallenge(meteor.id,emptySet(),rewarded=true),
            convoy=ArcadeConvoy(convoy.id,status=ConvoyStatus.Delivered,bonusAwarded=true),
            pending=listOf(PendingThreat(meteor,.75)),
            combat=ArcadeCombat(listOf(SpaceProjectile(ship.position,Vec2(40.0,0.0),ship.id,.8)),mapOf(ship.id to CraftStatus(5.0,.2,meteor.id))))
        val game=SpaceGameState(random=SessionRandom(73));game.resize(IntSize(900,1400));game.startSandbox(SandboxPresetKind.BinaryStars)
        val base=game.recoverySnapshot(123)!!
        return base.copy(mode=AppMode.Arcade,arcade=run,dirty=true,spawnKind=BodyKind.Ship)
    }
    @Test fun completeFleetShotsPendingAndOneTimeRewardsRoundTripOnDisk() {
        val saved=snapshot();storage.save(saved)
        val restored=storage.load()!!;assertEquals(saved,restored)
        val game=SpaceGameState();game.restoreRecovery(restored)
        assertTrue(game.menuOpen);assertFalse(game.motionSteeringEnabled)
        assertEquals(saved.arcade!!.combat,game.arcade!!.combat)
        assertEquals(saved.arcade!!.pending,game.arcade!!.pending)
        game.chooseArcadeUpgrade(ArcadeUpgrade.Guns);game.chooseArcadeUpgrade(ArcadeUpgrade.Guns)
        assertEquals(1,game.arcade!!.upgrades[ArcadeUpgrade.Guns]);assertNull(game.arcade!!.upgradeOffer)
        assertTrue(game.arcade!!.convoy!!.bonusAwarded)
        val maximum=(game.bodies+game.arcade!!.pending.map { it.body }).maxOf { it.id }
        assertTrue(SimulationEngine.newBodyId()>maximum)
    }
    @Test fun damagedCarrierAndCollectedSalvageKeepFormationTimerAndSingleReward() {
        val source=snapshot()
        var run=prepareCampaign(source.arcade!!.copy(elapsed=532.0,upgradeOffer=null,convoy=null),kotlin.random.Random(17))
        val lost=run.carrier!!.nodeIds.first()
        run=run.copy(bodies=run.bodies.filterNot { it.id == lost },carrier=run.carrier!!.copy(elapsed=35.0),
            salvage=ArcadeSalvage(13,Vec2(900.0,700.0),remaining=12.0,status=SalvageStatus.Collected,rewarded=true),
            salvageCollected=1,upgradeOffer=ArcadeUpgradeOffer(20,listOf(ArcadeUpgrade.Engines),salvageBonus=true))
        storage.save(source.copy(arcade=run));val restored=storage.load()!!
        assertEquals(run,restored.arcade);assertEquals(2,restored.arcade!!.carrierNodesRemaining)
        val selected=selectUpgrade(restored.arcade!!,ArcadeUpgrade.Engines)
        assertEquals(1,selected.upgrades[ArcadeUpgrade.Engines]);assertFalse(20 in selected.chosenUpgradeWaves)
        assertNull(offerUpgrade(selected,kotlin.random.Random(17)).upgradeOffer)
        val advanced=advanceArcade(selected,.05,kotlin.random.Random(17))
        assertEquals(35.05,advanced.carrier!!.elapsed,1e-7);assertEquals(2,advanced.carrierNodesRemaining)
    }
    @Test fun bothSolarAndThousandBodyWorldsRecoverWithoutTouchingManualSlots() {
        for (preset in listOf(SandboxPresetKind.SolarSystem,SandboxPresetKind.SystemGalaxy)) {
            val game=SpaceGameState();game.resize(IntSize(900,1400));game.startSandbox(preset,"Stored world")
            val before=game.recoverySnapshot(5)!!
            val slots=SandboxStorage(context);slots.save(1,game.snapshot(5)!!)
            storage.save(before);val loaded=storage.load()!!
            assertEquals(before,loaded);assertEquals(before.sandbox,slots.load(1))
        }
    }
    @Test fun corruptedAndFutureFilesFailSafelyAndAtomicBackupRestoresLastCompleteWrite() {
        val good=snapshot();storage.save(good)
        val bytes=disk.readBytes();File(disk.path+".bak").writeBytes(bytes);disk.writeText("partial")
        assertEquals(good,storage.load());assertFalse(storage.readFailed)
        val envelope=JSONObject(storage.encode(good)).put("sha256","broken").toString()
        disk.writeText(envelope);assertNull(storage.load());assertTrue(storage.readFailed)
        assertThrows(IllegalArgumentException::class.java) { storage.decode(JSONObject(storage.encode(good)).put("version",999).toString()) }
        storage.save(good);assertEquals(good,storage.load())
    }
    @Test fun restoredRandomKeepsFutureWaveChoicesAndEndlessAcknowledgement() {
        val saved=snapshot().copy(arcade=snapshot().arcade!!.copy(elapsed=560.0,endless=true,upgradeOffer=null))
        val random=SessionRandom(73);random.restore(saved.randomState!!)
        val game=SpaceGameState();game.restoreRecovery(storage.decode(storage.encode(saved)))
        assertFalse(game.arcadeCompletionPending);assertTrue(game.arcade!!.endless)
        assertEquals(random.state,game.recoverySnapshot(0)!!.randomState)
    }
}
