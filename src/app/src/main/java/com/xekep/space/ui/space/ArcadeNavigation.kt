package com.xekep.space.ui.space

import com.xekep.space.sim.*
import kotlin.math.*

/** Local steering, not teleporting or collision immunity. Guns aim independently of the hull.
 * Predict relative motion and gravity before selecting a corridor. Free autopilots avoid
 * world obstacles and craft; route navigators use temporary craft detours. Manual input wins.
 */
internal class ArcadeNavigation(scene: List<CelestialBody>) {
    private val obstacles=scene.filter { it.kind !in listOf(BodyKind.Meteor,BodyKind.Convoy) }
    private val acceleration=obstacles.associate { source -> source.id to
        if (source.kind == BodyKind.Core) Vec2.Zero else gravity(source.position,source) }

    private fun gravity(point: Vec2, craft: CelestialBody): Vec2 {
        var result=Vec2.Zero
        for (source in obstacles) if (source.id != craft.id) {
            val delta=source.position-point
            val soft=forceSoftening(craft,source)
            val square=delta.x*delta.x+delta.y*delta.y+soft*soft
            result+=delta*(SimulationEngine.gravitationalConstant*source.gravityMass/(square*sqrt(square)))
        }
        return result
    }

    /** Null means no intervention, preserving ordinary chase/patrol behaviour exactly. */
    fun avoid(craft: CelestialBody, desired: Vec2, steering: Double, vehiclesOnly: Boolean = false): Vec2? {
        if (obstacles.isEmpty() || desired.magnitude() < 2.0 || steering <= 0.0) return null
        val speed=desired.magnitude()
        val horizon=(craft.velocity.magnitude()/steering+.6).coerceIn(1.2,2.4)
        val nearby=obstacles.filter { it.id != craft.id && (!vehiclesOnly || it.isVehicle) && (it.position-craft.position).magnitude() <
            (maxOf(speed,craft.velocity.magnitude())+it.velocity.magnitude())*horizon+240.0+it.radius }
        if (nearby.isEmpty()) return null
        val radii=nearby.map { obstacle ->
            // Turning close to a massive core is too late even before surface contact.
            maxOf((craft.radius+obstacle.radius+18.0),
                sqrt(SimulationEngine.gravitationalConstant*obstacle.gravityMass/(steering*.8)))
        }
        fun clearance(wanted: Vec2): Double {
            var point=craft.position; var velocity=craft.velocity
            var minimum=Double.POSITIVE_INFINITY
            val step=horizon/10
            repeat(10) { index ->
                val correction=wanted-velocity
                val thrust=correction.normalized()*minOf(correction.magnitude()/step,steering)
                val pull=gravity(point,craft)
                point+=velocity*step+(thrust+pull)*(.5*step*step)
                velocity+=(thrust+pull)*step
                val time=(index+1)*step
                nearby.forEachIndexed { i,obstacle ->
                    val center=obstacle.position+(if (obstacle.kind == BodyKind.Core) Vec2.Zero else obstacle.velocity)*time+
                        (acceleration[obstacle.id] ?: Vec2.Zero)*(.5*time*time)
                    minimum=minOf(minimum,(point-center).magnitude()-radii[i])
                }
            }
            return minimum
        }
        val initial=clearance(desired)
        if (initial >= 12.0) return null
        val closest=nearby.minBy { (it.position-craft.position).magnitude()-it.radius }
        val radial=craft.position-closest.position
        val cross=radial.x*craft.velocity.y-radial.y*craft.velocity.x
        // Both pilots turn to the same local side on a meeting course, which separates them
        // in world space. Recomputing the side from a tiny cross product makes pilots oscillate.
        val side=if (closest.isVehicle) 1 else if (abs(cross) > 1.0) if (cross > 0) -1 else 1 else if (craft.id%2L == 0L) 1 else -1
        var best=desired; var bestScore=Double.NEGATIVE_INFINITY
        for (angle in listOf(30.0,60.0,90.0,120.0,160.0)) for (turn in listOf(side,-side)) {
            val candidate=rotateVector(desired,angle*turn*PI/180)
            val gap=clearance(candidate)
            // Safe corridors beat a shorter chase; prefer minimal detours and a stable orbit side.
            val score=if (gap >= 12.0) 10000-angle-(if (turn == side) 0.0 else 8.0)
                else gap-angle*.08
            if (score > bestScore) { bestScore=score; best=candidate }
        }
        return best
    }
}
