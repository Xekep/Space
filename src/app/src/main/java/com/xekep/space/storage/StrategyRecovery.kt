package com.xekep.space.storage

import com.xekep.space.sim.*
import com.xekep.space.ui.space.*
import org.json.JSONArray
import org.json.JSONObject

private fun point(p: Vec2)=JSONObject().put("x",p.x).put("y",p.y)
private fun JSONObject.position(): Vec2 = getJSONObject("position").let { Vec2(it.getDouble("x"),it.getDouble("y")) }.also { require(it.x in -1e6..1e6 && it.y in -1e6..1e6) }
private fun JSONObject.id()=getLong("id").also { require(it in 1..Long.MAX_VALUE-2001) }
private fun JSONArray.items(limit: Int): List<JSONObject> { require(length() <= limit); return List(length()) { getJSONObject(it) } }
internal fun encodeStrategy(battle: StrategyBattle)=JSONObject()
    .put("victory",battle.victory)
    .put("bases",JSONArray(battle.bases.map { JSONObject().put("id",it.id).put("position",point(it.position)).put("remaining",it.remaining).put("launches",it.launches) }))
    .put("stations",JSONArray(battle.stations.map { JSONObject().put("id",it.id).put("position",point(it.position)).put("kind",it.kind.name).put("level",it.level).put("hull",it.hull).put("product",it.product.name).put("remaining",it.remaining) }))
    .put("orders",JSONArray(battle.orders.map { (id,order) -> JSONObject().put("id",id).put("position",point(order.position)).put("command",order.command.name).put("target",order.targetId) }))
    .put("enemies",JSONArray(battle.enemies.map { (id,status) -> JSONObject().put("id",id).put("kind",status.kind.name).put("cooldown",status.cooldown).put("age",status.age) }))
internal fun decodeStrategy(obj: JSONObject, bodies: List<CelestialBody>): StrategyBattle {
    val ids=bodies.map { it.id }.toSet()
    val bases=obj.getJSONArray("bases").items(3).map { x -> EnemyBase(x.id().also { require(it in ids && bodies.first { b -> b.id == it }.kind == BodyKind.Meteor) },x.position(),
        x.getDouble("remaining").also { require(it in 0.0..60.0) },x.getInt("launches").also { require(it >= 0) }) }
    val stations=obj.getJSONArray("stations").items(4).map { x -> OrbitalStation(x.id().also { require(it !in ids) },x.position(),StationKind.valueOf(x.getString("kind")),
        x.getInt("level").also { require(it in 1..2) },x.getInt("hull").also { require(it in 1..4) },FactoryProduct.valueOf(x.getString("product")),x.getDouble("remaining").also { require(it in 0.0..18.0) }) }
    val orders=obj.getJSONArray("orders").items(36).associate { x -> x.id().also { require(it in ids && bodies.first { b -> b.id == it }.isVehicle) } to
        FleetOrder(FleetCommand.valueOf(x.getString("command")),x.position(),if (x.isNull("target")) null else x.getLong("target").also { require(it == -1L || it in 1..Long.MAX_VALUE-2001) }) }
    val enemies=obj.getJSONArray("enemies").items(24).associate { x -> x.id().also { require(it in ids && bodies.first { b -> b.id == it }.kind == BodyKind.Meteor) } to
        EnemyCraft(BodyKind.valueOf(x.getString("kind")).also { require(it in listOf(BodyKind.Ship,BodyKind.Rocket)) },x.getDouble("cooldown").also { require(it in 0.0..3.0) },x.getDouble("age").also { require(it in 0.0..100.0) }) }
    require(stations.map { it.id }.distinct().size == stations.size && bases.map { it.id }.distinct().size == bases.size)
    val victory=obj.getBoolean("victory"); require(!victory || bases.isEmpty())
    return StrategyBattle(bases,stations,orders,enemies,victory)
}
internal fun encodeSideEvent(event: ArcadeSideEvent)=JSONObject().put("kind",event.kind.name).put("position",point(event.position)).put("remaining",event.remaining).put("status",event.status.name)
internal fun decodeSideEvent(obj: JSONObject)=ArcadeSideEvent(SideEventKind.valueOf(obj.getString("kind")),obj.position(),obj.getDouble("remaining").also { require(it in 0.0..40.0) },SideEventStatus.valueOf(obj.getString("status")))
