package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.CelestialBody
import com.xekep.space.sim.Vec2
import kotlin.math.PI
import kotlin.math.sin

private const val PORTAL_SECONDS = .85
/** Presentation only: the threat already exists and travels independently of this effect. */
data class ThreatPortal(val position: Vec2, val radius: Float, val remaining: Double = PORTAL_SECONDS)

internal fun arrivalPortal(body: CelestialBody, run: ArcadeSession, view: ThreatView?): ThreatPortal? {
    if (view == null || view.viewport.width <= 0 || view.viewport.height <= 0) return null
    val point=worldToScreen(body.position,view.viewport,run.camera.center,run.camera.zoom,view.rotation)
    val radius=body.radius*run.camera.zoom
    return if (point.x+radius >= 0 && point.x-radius <= view.viewport.width &&
        point.y+radius >= 0 && point.y-radius <= view.viewport.height) ThreatPortal(body.position,body.radius) else null
}

internal fun DrawScope.drawArrivalPortals(game: SpaceGameState, camera: SpaceCamera=game.camera) {
    if (game.mode != AppMode.Arcade || (game.arcade?.lives ?: 0) <= 0) return
    game.arcade?.arrivalPortals.orEmpty().forEach { portal ->
        // The enclosing world Canvas already applies the pilot camera rotation.
        val point=worldToScreen(portal.position,game.viewport,camera.center,camera.zoom)
        val progress=(1-portal.remaining/PORTAL_SECONDS).coerceIn(0.0,1.0)
        val alpha=(portal.remaining/.2).coerceIn(0.0,1.0).toFloat()
        val radius=(portal.radius*camera.zoom+12.dp.toPx()).coerceIn(14.dp.toPx(),48.dp.toPx())*
            (.8+.2*sin(progress*PI)).toFloat()
        if (!Rect(-radius*2,-radius*2,size.width+radius*2,size.height+radius*2).contains(point)) return@forEach
        // Oblique, counter-rotating lens rings; no timer arc or pre-spawn countdown.
        drawCircle(Color(0xFF9768FF).copy(alpha=.10f*alpha),radius*1.6f,point)
        rotate((progress*140).toFloat(),point) {
            val lens=Size(radius*2,radius*1.25f)
            val origin=point-Offset(radius,radius*.625f)
            drawOval(Color(0xFF070419).copy(alpha=alpha),origin,lens)
            drawOval(Color(0xFFAD7AFF).copy(alpha=.8f*alpha),origin,lens,style=Stroke(2.dp.toPx()))
            drawArc(Color(0xFFC9A7FF).copy(alpha=alpha),30f,100f,false,origin,lens,style=Stroke(3.dp.toPx()))
            rotate((-progress*280-55).toFloat(),point) {
                drawOval(Color(0xFF668CFF).copy(alpha=.6f*alpha),origin,lens,style=Stroke(1.dp.toPx()))
            }
        }
    }
}
