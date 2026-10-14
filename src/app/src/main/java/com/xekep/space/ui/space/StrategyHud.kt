package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.*
import kotlin.math.*

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
    Surface(Modifier.fillMaxWidth().testTag("strategy-hud").blockWorldTouches(),shape=spaceShape(16.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TacticalButton(R.string.tactical_mode,"▣","tactical-toggle",game.tacticalMode,action=game::toggleTactics)
                if (game.tacticalMode) TacticalButton(R.string.select_fleet,"⋮⋮","fleet-all",action=game::selectFleet)
                Text(stringResource(R.string.strategy_status,run.bodies.count { it.isVehicle },battle.bases.size),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                TacticalButton(R.string.enemy_bases,"◎","enemy-focus",enabled=game.controlledVehicleId == null,action=game::focusEnemyBase)
            }
            if (game.tacticalMode) {
                val station=battle.stations.firstOrNull { it.id == game.selectedStationId }
                when {
                    station != null -> {
                        Text(stringResource(station.kind.labelId())+" · ${station.level} · ♥${station.hull}",style=MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            if (station.kind == StationKind.Factory) TextButton(onClick=game::cycleStationProduct,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("factory-product")) {
                                val kind=if (station.product == FactoryProduct.Rocket) BodyKind.Rocket else BodyKind.Ship
                                val mass=if (kind == BodyKind.Rocket) (if (station.level == 2) 24.0 else 18.0) else (if (station.level == 2) 60.0 else 36.0)
                                val cost=launchCost(kind,mass,if (station.product == FactoryProduct.Guardian) ShipClass.Guardian else ShipClass.Interceptor).toInt()
                                Text(stringResource(station.product.labelId())+if (station.product == FactoryProduct.Off) "" else " · $cost",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            TextButton(onClick=game::upgradeSelectedStation,enabled=station.level < 2 && run.energy >= 45,
                                modifier=Modifier.heightIn(min=48.dp).testTag("station-upgrade")) { Text("↑ 45") }
                            TacticalButton(R.string.close,"×","fleet-clear",action=game::clearFleet)
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
                        Text(stringResource(if (game.buildingKind != null) R.string.build_place else R.string.fleet_select_help),
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun DrawScope.drawStrategy(game: SpaceGameState, camera: SpaceCamera, animateLoot: Boolean = true) {
    val run=game.arcade ?: return
    val event=run.sideEvent?.takeIf { it.status == SideEventStatus.Active }
    event?.let {
        drawWorldLoot(it.kind.lootModel(),it.position,game,camera,run.elapsed,animateLoot)
    }
    val battle=run.strategy ?: return
    for (station in battle.stations) {
        val p=worldToScreen(station.position,game.viewport,camera.center,camera.zoom)
        val r=maxOf(12.dp.toPx(),38*camera.zoom)
        val color=when (station.kind) { StationKind.Factory -> Color(0xFF8BD3FF); StationKind.Service -> Color(0xFF80FFDF); StationKind.Bastion -> Color(0xFFFFD36B) }
        drawCircle(color.copy(alpha=.13f),r*1.6f,p)
        drawCircle(color,r,p,style=Stroke(2.dp.toPx()))
        drawCircle(color.copy(alpha=.3f),r*.65f,p)
        if (station.kind != StationKind.Service) {
            drawLine(color,p-Offset(r*.4f,0f),p+Offset(r*.4f,0f),3.dp.toPx())
        } else {
            drawLine(color,p-Offset(r*.4f,0f),p+Offset(r*.4f,0f),3.dp.toPx())
            drawLine(color,p-Offset(0f,r*.4f),p+Offset(0f,r*.4f),3.dp.toPx())
        }
        if (station.id == game.selectedStationId) drawCircle(Color.White,r+5.dp.toPx(),p,style=Stroke(1.dp.toPx()))
        if (station.kind == StationKind.Factory && station.product != FactoryProduct.Off) drawArc(color,-90f,
            (360*(1-station.remaining/(if (station.level == 2) 10.0 else 18.0))).toFloat().coerceIn(0f,360f),false,
            p-Offset(r*1.3f,r*1.3f),androidx.compose.ui.geometry.Size(r*2.6f,r*2.6f),style=Stroke(2.dp.toPx()))
        repeat(station.hull) { index -> drawCircle(color,1.5.dp.toPx(),p+Offset((index-(station.hull-1)*.5f)*5.dp.toPx(),r+9.dp.toPx())) }
    }
    for (base in battle.bases) {
        val body=run.bodies.firstOrNull { it.id == base.id } ?: continue
        val p=worldToScreen(base.position,game.viewport,camera.center,camera.zoom)
        val r=maxOf(18.dp.toPx(),38*camera.zoom)
        drawCircle(Color(0xFFFF8275).copy(alpha=.25f),r*1.3f,p,style=Stroke(2.dp.toPx()))
        repeat(4) { index ->
            val v=Offset(cos(index*PI/2).toFloat(),sin(index*PI/2).toFloat())
            drawLine(Color(0xFFFF8275),p+v*(r*.4f),p+v*r,4.dp.toPx())
        }
        drawCircle(Color(0xFFFFD6A0),r*.35f,p)
        val maximum=when (run.difficulty) { ArcadeDifficulty.Easy -> 3000; ArcadeDifficulty.Normal -> 4200; ArcadeDifficulty.Hard -> 5600 }
        drawArc(Color(0xFFFF8275),-90f,(360*body.mass/maximum).toFloat(),false,p-Offset(r*1.3f,r*1.3f),androidx.compose.ui.geometry.Size(r*2.6f,r*2.6f),style=Stroke(3.dp.toPx()))
    }
    run.bodies.filter { it.id in game.fleetSelection }.forEach { body ->
        val p=worldToScreen(body.position,game.viewport,camera.center,camera.zoom)
        drawCircle(Color(0xFF80FFDF),maxOf(14.dp.toPx(),body.radius*camera.zoom+5.dp.toPx()),p,style=Stroke(1.5.dp.toPx()))
        val order=battle.orders[body.id]
        if (order != null && game.tacticalMode) {
            val destination=run.bodies.firstOrNull { it.id == order.targetId }?.position ?: battle.stations.firstOrNull { it.id == order.targetId }?.position ?: order.position
            drawLine(Color(0xFF80FFDF).copy(alpha=.3f),p,worldToScreen(destination,game.viewport,camera.center,camera.zoom),1.dp.toPx())
        }
    }
    if (game.tacticalMode) game.touchPreview?.let { preview ->
        val start=worldToScreen(preview.startWorld,game.viewport,camera.center,camera.zoom)
        val end=worldToScreen(preview.currentWorld,game.viewport,camera.center,camera.zoom)
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
