package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.Explosion
import kotlin.math.*

/** Layered accretion disk, dark horizon and bent far-side light, drawn without bitmap assets. */
internal fun DrawScope.drawBlackHole(center: Offset,radius: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9160BD).copy(alpha=.13f),Color.Transparent),center,radius*3.5f),radius*3.5f,center)
    rotate(-20f,center) {
        val disk=center-Offset(radius*2.7f,radius*.72f)
        val extent=Size(radius*5.4f,radius*1.44f)
        drawOval(Color(0xFFE38B45).copy(alpha=.18f),disk,extent,style=Stroke(radius*.48f))
        drawOval(Color(0xFFEEAE65).copy(alpha=.55f),disk,extent,style=Stroke(radius*.18f))
        drawOval(Color(0xFFFFE8B6).copy(alpha=.65f),disk+Offset(0f,radius*.12f),Size(extent.width,extent.height-radius*.24f),style=Stroke(radius*.055f))
        // Far-side disk bends above the horizon; the near edge crosses in front.
        drawArc(Color(0xFFF1C186).copy(alpha=.75f),190f,160f,false,
            center-Offset(radius*1.55f,radius*1.6f),Size(radius*3.1f,radius*3.2f),style=Stroke(radius*.14f))
        drawCircle(Color(0xFF010208),radius,center)
        drawCircle(Color(0xFFFFDBA3).copy(alpha=.9f),radius*1.04f,center,style=Stroke(maxOf(.7.dp.toPx(),radius*.055f)))
        drawArc(Color(0xFFEFAB69).copy(alpha=.75f),0f,180f,false,disk,extent,style=Stroke(radius*.2f))
        drawArc(Color(0xFFFFF0CC),15f,130f,false,disk+Offset(0f,radius*.10f),
            Size(extent.width,extent.height-radius*.20f),style=Stroke(maxOf(.6.dp.toPx(),radius*.055f)))
    }
}

internal fun DrawScope.drawCollapse(burst: Explosion,center: Offset,zoom: Float,reducedFlashes: Boolean) {
    val progress=(burst.age/burst.duration).toFloat().coerceIn(0f,1f)
    val radius=(burst.collapseRadius*zoom).coerceIn(24.dp.toPx(),100.dp.toPx())
    val shrink=(1-progress).pow(2)
    val size=radius*shrink+9.dp.toPx()
    val alpha=(1-progress)*(if (reducedFlashes) .4f else .7f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD693).copy(alpha=alpha*.5f),Color.Transparent),center,size*1.7f),size*1.7f,center)
    if (progress < .55f) drawCircle(Color(0xFFFFC981).copy(alpha=alpha*(1-progress/.55f)),size,center)
    repeat(3) { ring ->
        val ringRadius=size*(1+ring*.25f)
        val angle=progress*500+ring*110
        drawArc(Color(0xFFDCB78C).copy(alpha=alpha*.6f),angle,100f,false,
            center-Offset(ringRadius,ringRadius*.6f),Size(ringRadius*2,ringRadius*1.2f),style=Stroke(1.dp.toPx()))
    }
    repeat(14) { particle ->
        val angle=particle*2*PI/14+progress*PI*4
        val distance=size*(1+particle%3*.2f)
        val point=center+Offset(cos(angle).toFloat(),sin(angle).toFloat())*distance
        drawCircle(Color(0xFFFFE2B6).copy(alpha=alpha),1.1.dp.toPx(),point)
    }
}
