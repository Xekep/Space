package com.xekep.space.ui.space

import com.xekep.space.sim.*
import kotlin.math.*

/** Local steering, not teleporting or collision immunity. Guns aim independently of the hull.
 * Predict relative motion and gravity before selecting a corridor. Free autopilots avoid
 * world obstacles and craft; route navigators use temporary craft detours. Manual input wins.
 */
internal class ArcadeNavigation(scene: List<CelestialBody>) {
    private val obstacles=scene.filter { it.kind != BodyKind.Meteor && it.kind != BodyKind.Convoy }
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
        // Obstacle forecasts do not depend on the proposed corridor. Share them across all
        // candidates; primitive coordinates avoid thousands of transient Vec2s per pilot.
        val step=horizon/10
        val centersX=DoubleArray(nearby.size*10)
        val centersY=DoubleArray(nearby.size*10)
        nearby.forEachIndexed { i,obstacle ->
            val velocity=if (obstacle.kind == BodyKind.Core) Vec2.Zero else obstacle.velocity
            val pull=acceleration[obstacle.id] ?: Vec2.Zero
            repeat(10) { index ->
                val time=(index+1)*step; val slot=index*nearby.size+i
                centersX[slot]=obstacle.position.x+velocity.x*time+pull.x*(.5*time*time)
                centersY[slot]=obstacle.position.y+velocity.y*time+pull.y*(.5*time*time)
            }
        }
        val softSquares=DoubleArray(obstacles.size) { forceSoftening(craft,obstacles[it]).let { soft -> soft*soft } }
        fun clearance(wanted: Vec2): Double {
            var x=craft.position.x; var y=craft.position.y
            var vx=craft.velocity.x; var vy=craft.velocity.y
            var minimum=Double.POSITIVE_INFINITY
            repeat(10) { index ->
                val dx=wanted.x-vx; val dy=wanted.y-vy; val length=sqrt(dx*dx+dy*dy)
                val scale=if (length <= 1e-9) 0.0 else minOf(length/step,steering)/length
                var ax=dx*scale; var ay=dy*scale
                for (j in obstacles.indices) {
                    val source=obstacles[j]
                    if (source.id == craft.id) continue
                    val sx=source.position.x-x; val sy=source.position.y-y
                    val square=sx*sx+sy*sy+softSquares[j]
                    val factor=SimulationEngine.gravitationalConstant*source.gravityMass/(square*sqrt(square))
                    ax+=sx*factor; ay+=sy*factor
                }
                x+=vx*step+ax*(.5*step*step); y+=vy*step+ay*(.5*step*step)
                vx+=ax*step; vy+=ay*step
                for (i in nearby.indices) {
                    val slot=index*nearby.size+i; val sx=x-centersX[slot]; val sy=y-centersY[slot]
                    minimum=minOf(minimum,sqrt(sx*sx+sy*sy)-radii[i])
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
            // Angles are ordered by the original safe-corridor score (side penalty <
            // the next angle gap). The first safe corridor is already the global winner.
            if (gap >= 12.0) return candidate
            val score=gap-angle*.08
            if (score > bestScore) { bestScore=score; best=candidate }
        }
        return best
    }
}
