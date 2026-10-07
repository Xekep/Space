package com.xekep.space.sim

import kotlin.math.sqrt

enum class SatellitePlacement { Clear, Overlap, StrongTides }

/** A local orbit assist, with a conservative check for disturbing differential gravity. */
fun satellitePlacement(parent: CelestialBody, satellite: CelestialBody, scene: List<CelestialBody>): SatellitePlacement {
    val offset=satellite.position-parent.position
    val distance=offset.magnitude()
    val margin=if (parent.physicalScale || satellite.physicalScale) .0001 else minOf(8.0,parent.radius*.2)
    if (distance <= parent.radius+satellite.radius+margin || scene.any {
        it.id != parent.id && it.id != satellite.id &&
            (it.position-satellite.position).magnitude() <= it.radius+satellite.radius
    }) return SatellitePlacement.Overlap
    val smoothing=forceSoftening(parent,satellite)
    val square=distance*distance+smoothing*smoothing
    val attraction=SimulationEngine.gravitationalConstant*(parent.gravityMass+satellite.gravityMass)*distance/(square*sqrt(square))
    var tides=Vec2.Zero
    for (other in scene) {
        if (other.id == parent.id || other.id == satellite.id) continue
        fun acceleration(body: CelestialBody): Vec2 {
            val delta=other.position-body.position
            val softening=forceSoftening(other,body)
            val squared=delta.x*delta.x+delta.y*delta.y+softening*softening
            return delta*(SimulationEngine.gravitationalConstant*other.gravityMass/(squared*sqrt(squared)))
        }
        tides += acceleration(satellite)-acceleration(parent)
    }
    return if (!attraction.isFinite() || attraction <= 0.0 || !tides.magnitude().isFinite() ||
        tides.magnitude() > attraction*.2) SatellitePlacement.StrongTides else SatellitePlacement.Clear
}
