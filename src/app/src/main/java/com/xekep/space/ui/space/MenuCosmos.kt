package com.xekep.space.ui.space

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/** A small analytic star system, independent of the paused game and its potentially large solver. */
@Composable
internal fun rememberMenuPhase(): State<Float> {
    val transition=rememberInfiniteTransition(label="menu-cosmos")
    val raw=transition.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(90000,easing=LinearEasing)),label="cosmos-phase")
    return remember { derivedStateOf { floor(raw.value*1800/(2*PI).toFloat())*(2*PI).toFloat()/1800 } }
}

@Composable
internal fun MenuCosmos(modifier: Modifier,phase: State<Float>,retroConsole: Boolean = false,economy: Boolean = false) {
    val renderer=remember(retroConsole) { if (retroConsole) RetroRenderer() else null }
    val points=remember { val random=Random(79); List(100) { Triple(random.nextFloat(),random.nextFloat(),random.nextFloat()) } }
    Canvas(modifier.testTag("menu-cosmos")) {
        drawRetroFrame(renderer) {
        val t=phase.value
        drawRect(Brush.verticalGradient(listOf(Color(0xFF020611),Color(0xFF0A1530),Color(0xFF030A19))))
        val anchor=Offset(size.width*.22f,size.height*.19f)
        val companion=Offset(size.width*.81f,size.height*.79f)
        drawCircle(Brush.radialGradient(listOf(Color(0x303167AA),Color.Transparent),anchor,size.minDimension*.65f),size.minDimension*.65f,anchor)
        drawCircle(Brush.radialGradient(listOf(Color(0x25215B8B),Color.Transparent),companion,size.minDimension*.55f),size.minDimension*.55f,companion)
        points.forEachIndexed { i,(x,y,brightness) ->
            if (economy && i%2 != 0) return@forEachIndexed
            val point=Offset(x*size.width+sin(t+i)*7.dp.toPx(),y*size.height+cos(t+i)*9.dp.toPx())
            drawCircle(Color.White.copy(alpha=.16f+brightness*.5f),(.35f+brightness*.7f).dp.toPx(),point)
        }
        fun star(center: Offset,radius: Float,color: Color) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.24f),color.copy(alpha=.04f),Color.Transparent),center,radius*8),radius*8,center)
            drawCircle(color.copy(alpha=.09f),radius*2.2f,center)
            drawCircle(color,radius,center)
            drawCircle(Color.White.copy(alpha=.75f),radius*.5f,center)
        }
        val sun=anchor+Offset(cos(t)*size.width*.045f,sin(t)*size.height*.022f)
        star(sun,7.dp.toPx(),Color(0xFFFFD898))
        star(companion+Offset(sin(t)*size.width*.05f,cos(t)*size.height*.03f),5.dp.toPx(),Color(0xFF8BD3FF))
        repeat(8) { i ->
            val axis=size.minDimension*(.12f+i*.052f)
            val angle=t*(if (i < 3) 2f else 1f)+i*2.39f
            val flatten=if (i < 4) .66f else .44f
            drawOval(Color(0xFF8BD3FF).copy(alpha=.045f),sun-Offset(axis,axis*flatten),
                androidx.compose.ui.geometry.Size(axis*2,axis*flatten*2),style=Stroke(.5.dp.toPx()))
            val planet=sun+Offset(cos(angle)*axis,sin(angle)*axis*flatten)
            val color=when (i%3) { 0 -> Color(0xFF8BD3FF); 1 -> Color(0xFFD9B2EE); else -> Color(0xFFB0C9B0) }
            val r=(1.8f+i%3*.6f).dp.toPx()
            drawCircle(color.copy(alpha=.1f),r*2.8f,planet)
            drawCircle(color.copy(alpha=.8f),r,planet)
        }
        }
    }
}
