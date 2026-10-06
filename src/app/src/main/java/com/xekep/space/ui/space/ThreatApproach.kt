package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import kotlin.math.abs

internal data class ThreatView(val viewport: IntSize, val rotation: Double = 0.0)

/** Move the incoming trajectory back beyond both the arena and the currently visible world. */
internal fun distantThreat(body: CelestialBody, session: ArcadeSession, view: ThreatView?): CelestialBody {
    val center=Vec2(session.arena.width/2.0,session.arena.height/2.0)
    val corners=mutableListOf(Vec2.Zero,Vec2(session.arena.width.toDouble(),session.arena.height.toDouble()))
    if (view != null && view.viewport != IntSize.Zero) {
        for (x in listOf(0f,view.viewport.width.toFloat())) for (y in listOf(0f,view.viewport.height.toFloat()))
            corners += screenToWorld(Offset(x,y),view.viewport,session.camera.center,session.camera.zoom,view.rotation)
    }
    val minX=corners.minOf { it.x }; val maxX=corners.maxOf { it.x }
    val minY=corners.minOf { it.y }; val maxY=corners.maxOf { it.y }
    val inward=body.velocity.normalized()
    if (inward.magnitude()<.5) return body
    val closest=body.position+inward*((center-body.position).let { it.x*inward.x+it.y*inward.y })
    val aim=closest.takeIf { it.x in minX..maxX && it.y in minY..maxY } ?: center
    val outward=inward * -1.0
    val exitX=if (abs(outward.x)<1e-9) Double.POSITIVE_INFINITY else ((if (outward.x>0) maxX else minX)-aim.x)/outward.x
    val exitY=if (abs(outward.y)<1e-9) Double.POSITIVE_INFINITY else ((if (outward.y>0) maxY else minY)-aim.y)/outward.y
    val lead=maxOf(350.0,body.velocity.magnitude()*4.0)+body.radius*2.0
    val point=aim+outward*(minOf(exitX,exitY)+lead)
    return body.copy(position=point,trail=listOf(point))
}

/** First future crossing of the actual screen border, along the incoming velocity. */
internal fun incomingScreenEntry(point: Vec2, velocity: Vec2, width: Double, height: Double): Vec2? {
    if (width<=0 || height<=0 || !point.x.isFinite() || !point.y.isFinite() || !velocity.x.isFinite() || !velocity.y.isFinite()) return null
    if (point.x in 0.0..width && point.y in 0.0..height) return null
    var enter=0.0; var leave=Double.POSITIVE_INFINITY
    for ((position,speed,extent) in listOf(Triple(point.x,velocity.x,width),Triple(point.y,velocity.y,height))) {
        if (abs(speed)<1e-9) { if (position !in 0.0..extent) return null }
        else {
            val a=-position/speed; val b=(extent-position)/speed
            enter=maxOf(enter,minOf(a,b)); leave=minOf(leave,maxOf(a,b))
            if (leave<enter) return null
        }
    }
    if (!leave.isFinite() || leave<0 || enter>leave) return null
    return point+velocity*enter
}
