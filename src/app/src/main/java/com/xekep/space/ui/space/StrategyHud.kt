package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.*
import kotlin.math.*

private val EnemyBaseRole?.labelId: Int get() = when (this) {
    EnemyBaseRole.Silo -> R.string.strategy_silo
    EnemyBaseRole.Shipyard -> R.string.strategy_shipyard
    EnemyBaseRole.Citadel -> R.string.strategy_citadel
    null -> R.string.strategy_enemy_base
}

private val EnemyBaseRole?.productionLabelId: Int get() = when (this) {
    EnemyBaseRole.Silo -> R.string.strategy_silo_production
    EnemyBaseRole.Shipyard -> R.string.strategy_shipyard_production
    EnemyBaseRole.Citadel, null -> R.string.strategy_mixed_production
}

@Composable
internal fun StrategyTrainingHud(game: SpaceGameState) {
    val message = game.tutorialText ?: return
    Surface(Modifier.fillMaxWidth().testTag("strategy-training-hint").blockWorldTouches(),
        shape = spaceShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = game::skipTutorial) { Text(stringResource(R.string.skip)) }
        }
    }
}

internal fun SideEventKind.labelId()=when(this) { SideEventKind.Supply -> R.string.event_supply; SideEventKind.Wreck -> R.string.event_wreck; SideEventKind.Rescue -> R.string.event_rescue }
internal fun StationKind.labelId()=when(this) { StationKind.Factory -> R.string.station_factory; StationKind.Service -> R.string.station_service; StationKind.Bastion -> R.string.station_bastion }
internal fun FactoryProduct.labelId()=when(this) { FactoryProduct.Off -> R.string.production_off; FactoryProduct.Rocket -> R.string.spawn_rocket; FactoryProduct.Interceptor -> R.string.spawn_ship; FactoryProduct.Guardian -> R.string.spawn_guardian }

@Composable
internal fun SideEventHud(game: SpaceGameState) {
    val event=game.arcade?.sideEvent?.takeIf { it.status == SideEventStatus.Active } ?: return
    val label=stringResource(event.kind.labelId())
    val help=stringResource(if (event.kind == SideEventKind.Rescue) R.string.event_rescue_help else R.string.event_collect_help)
    Surface(onClick=game::focusSideEvent,enabled=game.controlledVehicleId == null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("side-event").blockWorldTouches()
        .semantics { contentDescription=help },shape=spaceShape(10.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.85f),contentColor=MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("◇",color=MaterialTheme.colorScheme.secondary)
            Text(label,Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.labelMedium)
            Text("${ceil(event.remaining).toInt()}s",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun TacticalButton(label: Int, glyph: String, tag: String, active: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    val description=stringResource(label)
    Surface(onClick=action,enabled=enabled,modifier=Modifier.size(48.dp).testTag(tag).semantics { contentDescription=description; selected=active },
        shape=spaceShape(10.dp),color=if (active) MaterialTheme.colorScheme.secondary.copy(alpha=.22f) else Color.Transparent) {
        Box(contentAlignment=Alignment.Center) { Text(glyph,style=MaterialTheme.typography.titleLarge,
            color=if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f)) }
    }
}

@Composable
internal fun StrategyHud(game: SpaceGameState) {
    val run=game.arcade ?: return
    val battle=run.strategy ?: return
    val selection=game.fleetSelection.intersect(run.bodies.filter { it.isVehicle }.map { it.id }.toSet())
    val target = battle.bases.firstOrNull { it.id == game.selectedBodyId && game.selectedStationId == null }
    Surface(Modifier.fillMaxWidth().testTag("strategy-hud").blockWorldTouches(),shape=spaceShape(16.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TacticalButton(R.string.tactical_mode,"▣","tactical-toggle",game.tacticalMode,action=game::toggleTactics)
                if (game.tacticalMode) TacticalButton(R.string.select_fleet,"⋮⋮","fleet-all",action=game::selectFleet)
                Column(Modifier.weight(1f)) {
                    if (target == null) {
                        Text(stringResource(R.string.strategy_status,run.bodies.count { it.isVehicle }), style=MaterialTheme.typography.labelMedium)
                    } else {
                        Text(stringResource(target.role.labelId),Modifier.testTag("strategy-base-role"),
                            style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(stringResource(R.string.strategy_base_schedule,stringResource(target.role.productionLabelId),
                            target.role.reinforcementSeconds(run.difficulty)),style=MaterialTheme.typography.labelSmall)
                    }
                }
                TacticalButton(R.string.strategy_next_base,"◎","enemy-focus",
                    enabled=game.controlledVehicleId == null && battle.bases.isNotEmpty(),action=game::focusEnemyBase)
            }
            if (game.tacticalMode) {
                val station=battle.stations.firstOrNull { it.id == game.selectedStationId }
                when {
                    station != null -> {
                        Text(stringResource(station.kind.labelId())+" · ${station.level} · ♥${station.hull}",style=MaterialTheme.typography.labelMedium)
                        val output = station.factoryOutput()
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            if (station.kind == StationKind.Factory) TextButton(onClick=game::cycleStationProduct,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("factory-product")) {
                                val price = output?.let { " · ${ceil(it.cost).toInt()}" }.orEmpty()
                                Text(stringResource(station.product.labelId()) + price, maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            TextButton(onClick=game::upgradeSelectedStation,enabled=station.level < 2 && run.energy >= 45,
                                modifier=Modifier.heightIn(min=48.dp).testTag("station-upgrade")) { Text("↑ 45") }
                            TacticalButton(R.string.close,"×","fleet-clear",action=game::clearFleet)
                        }
                        val core = run.bodies.firstOrNull { it.kind == BodyKind.Core }
                        if (output != null && core != null) {
                            val status = output.status(station.remaining, run.bodies, run.energy, run.launchLimit(output.kind), station.factoryExit(core.position))
                            val label = when (status) {
                                FactoryStatus.Building -> stringResource(R.string.factory_countdown, ceil(station.remaining).toInt())
                                FactoryStatus.NeedsEnergy -> stringResource(R.string.factory_energy_required, output.requiredEnergy)
                                FactoryStatus.FleetFull -> stringResource(R.string.factory_fleet_full)
                                FactoryStatus.ExitBlocked -> stringResource(R.string.factory_exit_blocked)
                                FactoryStatus.Ready -> stringResource(R.string.factory_ready)
                            }
                            Text(label, Modifier.testTag("factory-status"), style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    selection.isNotEmpty() -> {
                        Text(stringResource(R.string.fleet_selected,selection.size),style=MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TacticalButton(R.string.order_move,"➜","order-move",game.fleetCommand == FleetCommand.Move) { game.chooseFleetCommand(FleetCommand.Move) }
                            TacticalButton(R.string.order_guard,"◈","order-guard",game.fleetCommand == FleetCommand.Guard) { game.chooseFleetCommand(FleetCommand.Guard) }
                            TacticalButton(R.string.order_attack,"⊕","order-attack",game.fleetCommand == FleetCommand.Attack) { game.chooseFleetCommand(FleetCommand.Attack) }
                            TacticalButton(R.string.order_auto,"↻","order-auto",action=game::fleetAuto)
                            TacticalButton(R.string.craft_manual,"✈","fleet-pilot",enabled=selection.size == 1,action=game::pilotSelectedCraft)
                            TacticalButton(R.string.close,"×","fleet-clear",action=game::clearFleet)
                        }
                        if (battle.training == null)
                            Text(stringResource(R.string.order_target),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={ game.chooseBuilding(StationKind.Factory) },enabled=battle.stations.size < 4 && run.energy >= 65,
                                contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("build-factory").semantics { selected=game.buildingKind == StationKind.Factory }) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(stringResource(R.string.station_factory),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("65",style=MaterialTheme.typography.labelSmall) } }
                            TextButton(onClick={ game.chooseBuilding(StationKind.Service) },enabled=battle.stations.size < 4 && run.energy >= 55,
                                contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("build-service").semantics { selected=game.buildingKind == StationKind.Service }) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(stringResource(R.string.station_service),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("55",style=MaterialTheme.typography.labelSmall) } }
                            TextButton(onClick={ game.chooseBuilding(StationKind.Bastion) },enabled=battle.stations.size < 4 && run.energy >= 70,
                                contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("build-bastion").semantics { selected=game.buildingKind == StationKind.Bastion }) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(stringResource(R.string.station_bastion),style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("70",style=MaterialTheme.typography.labelSmall) } }
                        }
                        if (game.buildingKind != null || battle.training == null && target == null)
                            Text(stringResource(if (game.buildingKind != null) R.string.build_place else R.string.fleet_select_help),
                                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private enum class StationSilhouette(vararg vertices: Float) {
    Docks(-1f,-.60f, -.68f,-.90f, .68f,-.90f, 1f,-.60f, 1f,.54f, .56f,.88f, -.56f,.88f, -1f,.54f),
    Service(-.34f,-1f, .34f,-1f, .34f,-.35f, 1f,-.35f, 1f,.35f, .34f,.35f, .34f,1f, -.34f,1f,
        -.34f,.35f, -1f,.35f, -1f,-.35f, -.34f,-.35f),
    Shield(-.91f,-.58f, -.59f,-.92f, .59f,-.92f, .91f,-.58f, .81f,.38f, 0f,1f, -.81f,.38f),
    Silo(0f,-1f, .99f,0f, 0f,1f, -.99f,0f),
    Outpost(-.6f,-.84f, .6f,-.84f, 1f,0f, .6f,.84f, -.6f,.84f, -1f,0f);

    val hull by lazy {
        Path().apply {
            moveTo(vertices[0],vertices[1])
            for (i in 2 until vertices.size step 2) lineTo(vertices[i],vertices[i+1])
            close()
        }
    }
}

private fun DrawScope.drawStationHull(center: Offset, radius: Float, color: Color, shape: StationSilhouette) {
    fun point(x: Float, y: Float) = center + Offset(x * radius, y * radius)
    val stroke = 1.8.dp.toPx() / radius
    translate(center.x,center.y) {
        scale(radius,radius,pivot=Offset.Zero) {
            drawPath(shape.hull,Color(0xFF101D30))
            drawPath(shape.hull,color.copy(alpha = .24f))
            drawPath(shape.hull,color,style = Stroke(stroke))
        }
    }
    fun panel(x: Float, y: Float, width: Float, height: Float, light: Boolean = false) {
        drawRect(if (light) color else Color(0xFF0B1728),point(x,y),Size(width*radius,height*radius))
    }
    when (shape) {
        StationSilhouette.Docks -> {
            for (i in 0..1) {
                val x = -.73f+i*.90f
                panel(x,-.48f,.56f,.90f)
                panel(x,.44f,.56f,.15f,light=true)
            }
            panel(-.09f,-.67f,.18f,1.3f,light=true)
        }
        StationSilhouette.Service -> {
            panel(-.29f,-.20f,.58f,.40f)
            drawLine(color,point(-.17f,0f),point(.17f,0f),1.7.dp.toPx())
            drawLine(color,point(0f,-.17f),point(0f,.17f),1.7.dp.toPx())
        }
        StationSilhouette.Shield -> {
            drawCircle(Color(0xFF0B1728),radius*.38f,center)
            drawLine(color,point(0f,.23f),point(0f,-.62f),3.dp.toPx())
            panel(-.63f,-.41f,.24f,.46f,light=true)
            panel(.39f,-.41f,.24f,.46f,light=true)
        }
        StationSilhouette.Silo -> {
            for (i in 0..2) {
                val x = -.48f+i*.38f
                panel(x,-.27f,.20f,.54f)
                panel(x,-.35f,.20f,.15f,light=true)
            }
        }
        StationSilhouette.Outpost -> drawCircle(color,radius*.31f,center,style = Stroke(2.dp.toPx()))
    }
}

internal fun DrawScope.drawStrategy(game: SpaceGameState, camera: SpaceCamera, animateLoot: Boolean = true) {
    val run=game.arcade ?: return
    fun project(position: Vec2) =
        worldToScreen(position,game.viewport,camera.center,camera.zoom,game.cameraRotation)
    val event=run.sideEvent?.takeIf { it.status == SideEventStatus.Active }
    event?.let {
        drawWorldLoot(it.kind.lootModel(),it.position,game,camera,run.elapsed,animateLoot)
    }
    val battle=run.strategy ?: return
    for (station in battle.stations) {
        val p=project(station.position)
        val r=maxOf(12.dp.toPx(),38*camera.zoom)
        val color=when (station.kind) { StationKind.Factory -> Color(0xFF8BD3FF); StationKind.Service -> Color(0xFF80FFDF); StationKind.Bastion -> Color(0xFFFFD36B) }
        val silhouette = when (station.kind) {
            StationKind.Factory -> StationSilhouette.Docks
            StationKind.Service -> StationSilhouette.Service
            StationKind.Bastion -> StationSilhouette.Shield
        }
        drawStationHull(p,r,color,silhouette)
        if (station.id == game.selectedStationId) drawCircle(Color.White,r*1.26f,p,style=Stroke(1.dp.toPx()))
        if (battle.training == StrategyTraining.Factory && station.kind == StationKind.Factory)
            drawCircle(Color(0xFFFFD36B), r*1.46f, p, style=Stroke(2.dp.toPx()))
        if (station.kind == StationKind.Factory && station.product != FactoryProduct.Off) drawArc(color,-90f,
            (360*(1-station.remaining/(if (station.level == 2) 10.0 else 18.0))).toFloat().coerceIn(0f,360f),false,
            p-Offset(r*1.14f,r*1.14f),Size(r*2.28f,r*2.28f),style=Stroke(1.5.dp.toPx()))
        repeat(station.hull) { index -> drawCircle(color,1.3.dp.toPx(),p+Offset((index-(station.hull-1)*.5f)*4.dp.toPx(),r+6.dp.toPx())) }
    }
    for (base in battle.bases) {
        val body=run.bodies.firstOrNull { it.id == base.id } ?: continue
        val p=project(base.position)
        val r=maxOf(18.dp.toPx(),38*camera.zoom)
        val silhouette = when (base.role) {
            EnemyBaseRole.Silo -> StationSilhouette.Silo
            EnemyBaseRole.Shipyard -> StationSilhouette.Docks
            EnemyBaseRole.Citadel -> StationSilhouette.Shield
            null -> StationSilhouette.Outpost
        }
        val color = Color(0xFFFF8275)
        drawStationHull(p,r,color,silhouette)
        val ring = r*1.25f
        drawCircle(color.copy(alpha=.22f),ring,p,style=Stroke(2.dp.toPx()))
        drawArc(color,-90f,(360*body.mass/base.role.maxHull(run.difficulty)).toFloat().coerceIn(0f,360f),
            false,p-Offset(ring,ring),Size(ring*2,ring*2),style=Stroke(2.dp.toPx()))
        if (base.id == game.selectedBodyId)
            drawCircle(Color.White,r*1.45f,p,style=Stroke(1.2.dp.toPx()))
    }
    run.bodies.filter { it.id in game.fleetSelection }.forEach { body ->
        val p=project(body.position)
        drawCircle(Color(0xFF80FFDF),maxOf(14.dp.toPx(),body.radius*camera.zoom+5.dp.toPx()),p,style=Stroke(1.5.dp.toPx()))
        val order=battle.orders[body.id]
        if (order != null && game.tacticalMode) {
            val destination=run.bodies.firstOrNull { it.id == order.targetId }?.position ?: battle.stations.firstOrNull { it.id == order.targetId }?.position ?: order.position
            drawLine(Color(0xFF80FFDF).copy(alpha=.3f),p,project(destination),1.dp.toPx())
        }
    }
    if (game.tacticalMode) game.touchPreview?.let { preview ->
        val start=project(preview.startWorld)
        val end=project(preview.currentWorld)
        val left=minOf(start.x,end.x); val top=minOf(start.y,end.y)
        drawRect(Color(0xFF80FFDF).copy(alpha=.12f),Offset(left,top),androidx.compose.ui.geometry.Size(abs(start.x-end.x),abs(start.y-end.y)))
        if (game.buildingKind != null) drawCircle(if (canBuildStation(run,game.buildingKind!!,preview.currentWorld)) Color(0xFF80FFDF) else Color(0xFFFF8275),maxOf(12.dp.toPx(),38*camera.zoom),end,style=Stroke(2.dp.toPx()))
    }
}

@Composable
internal fun StrategyVictory(game: SpaceGameState, victory: Boolean = true) {
    AlertDialog(onDismissRequest=game::openMenu,modifier=Modifier.testTag(if (victory) "strategy-victory" else "strategy-defeat"),title={ Text(stringResource(if (victory) R.string.strategy_victory else R.string.strategy_defeat)) },
        text={ Text(if (victory) stringResource(R.string.strategy_victory_help) else stringResource(R.string.strategy_defeat_help,game.arcade?.strategy?.bases?.size ?: 0)) },
        confirmButton={ TextButton(onClick=game::restartCombat) { Text(stringResource(R.string.strategy_replay)) } },
        dismissButton={ TextButton(onClick=game::openMenu) { Text(stringResource(R.string.menu)) } })
}
