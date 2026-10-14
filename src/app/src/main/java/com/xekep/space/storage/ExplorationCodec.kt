package com.xekep.space.storage

import com.xekep.space.sim.*
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeExploration(state: ExplorationState)=JSONObject().put("seed",state.seed)
    .put("origin",JSONObject().put("x",state.origin.x).put("y",state.origin.y))
    .put("visited",JSONArray(state.visited.toList()))

internal fun decodeExploration(obj: JSONObject): ExplorationState {
    val point=obj.getJSONObject("origin")
    val origin=Vec2(point.getDouble("x"),point.getDouble("y"))
    require(origin.x in -1e9..1e9 && origin.y in -1e9..1e9)
    val array=obj.getJSONArray("visited");require(array.length()<=MAX_EXPLORATION_REGIONS)
    val keys=(0 until array.length()).map { array.getLong(it).also { key -> require(validRegionKey(key)) } }
    require(keys.distinct().size==keys.size)
    return ExplorationState(obj.getLong("seed"),origin,keys.toSet())
}
