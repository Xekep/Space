package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.xekep.space.R

internal val LocalRetroUi=staticCompositionLocalOf { false }
internal val RetroFont=FontFamily(Font(R.font.retro_pixel))

@Composable
internal fun RetroUiTheme(enabled: Boolean, content: @Composable () -> Unit) {
    val original=MaterialTheme.typography
    val originalShapes=MaterialTheme.shapes
    val typography=remember(original) {
        fun TextStyle.pixel()=copy(fontFamily=RetroFont,fontWeight=FontWeight.Normal,
            fontSynthesis=FontSynthesis.None,letterSpacing=0.sp)
        original.copy(displayLarge=original.displayLarge.pixel(),displayMedium=original.displayMedium.pixel(),
            displaySmall=original.displaySmall.pixel(),headlineLarge=original.headlineLarge.pixel(),
            headlineMedium=original.headlineMedium.pixel(),headlineSmall=original.headlineSmall.pixel(),
            titleLarge=original.titleLarge.pixel(),titleMedium=original.titleMedium.pixel(),titleSmall=original.titleSmall.pixel(),
            bodyLarge=original.bodyLarge.pixel(),bodyMedium=original.bodyMedium.pixel(),bodySmall=original.bodySmall.pixel(),
            labelLarge=original.labelLarge.pixel(),labelMedium=original.labelMedium.pixel(),labelSmall=original.labelSmall.pixel())
    }
    CompositionLocalProvider(LocalRetroUi provides enabled) {
        // Keep the same composition tree so changing the theme retains open sheets and scroll positions.
        MaterialTheme(typography=if (enabled) typography else original,
            shapes=if (enabled) Shapes(PixelPanelShape,PixelPanelShape,PixelPanelShape,PixelPanelShape,PixelPanelShape) else originalShapes,
            content=content)
    }
}

/** Two square steps at every corner; dimensions and touch bounds are unchanged. */
internal val PixelPanelShape: CornerBasedShape=PixelCornerShape()

private class PixelCornerShape(topStart: CornerSize=CornerSize(6.dp), topEnd: CornerSize=CornerSize(6.dp),
    bottomEnd: CornerSize=CornerSize(6.dp), bottomStart: CornerSize=CornerSize(6.dp)):
    CornerBasedShape(topStart,topEnd,bottomEnd,bottomStart) {
    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        PixelCornerShape(topStart,topEnd,bottomEnd,bottomStart)
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float,
        bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        val ltr=layoutDirection == LayoutDirection.Ltr
        fun cut(value: Float)=minOf(value,size.minDimension/3)
        val tl=cut(if (ltr) topStart else topEnd); val tr=cut(if (ltr) topEnd else topStart)
        val bl=cut(if (ltr) bottomStart else bottomEnd); val br=cut(if (ltr) bottomEnd else bottomStart)
        val w=size.width; val h=size.height
        return Outline.Generic(Path().apply {
            moveTo(tl,0f); lineTo(w-tr,0f); lineTo(w-tr,tr/3); lineTo(w-tr/3,tr/3); lineTo(w-tr/3,tr); lineTo(w,tr)
            lineTo(w,h-br); lineTo(w-br/3,h-br); lineTo(w-br/3,h-br/3); lineTo(w-br,h-br/3); lineTo(w-br,h)
            lineTo(bl,h); lineTo(bl,h-bl/3); lineTo(bl/3,h-bl/3); lineTo(bl/3,h-bl); lineTo(0f,h-bl)
            lineTo(0f,tl); lineTo(tl/3,tl); lineTo(tl/3,tl/3); lineTo(tl,tl/3); close()
        })
    }
}

@Composable internal fun spaceShape(radius: Dp): Shape =
    if (LocalRetroUi.current) PixelPanelShape else RoundedCornerShape(radius)

@Composable internal fun spaceShape(topStart: Dp, topEnd: Dp): Shape =
    if (LocalRetroUi.current) PixelPanelShape.copy(bottomStart=CornerSize(0.dp),bottomEnd=CornerSize(0.dp))
    else RoundedCornerShape(topStart=topStart,topEnd=topEnd)

@Composable internal fun Modifier.retroFrame(color: Color=MaterialTheme.colorScheme.primary): Modifier =
    if (LocalRetroUi.current) border(1.dp,color.copy(alpha=.5f),PixelPanelShape) else this

/** Icons get a finer pixel grid than the world; typography is drawn from a real pixel font. */
@Composable
internal fun PixelCanvas(modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    val retro=LocalRetroUi.current
    val renderer=remember(retro) { if (retro) RetroRenderer(1.5.dp) else null }
    Canvas(modifier) { drawRetroFrame(renderer,onDraw) }
}

@Composable
internal fun SpaceSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier=Modifier, enabled: Boolean=true) {
    if (!LocalRetroUi.current) { Switch(checked,onCheckedChange,modifier,enabled=enabled); return }
    val accent=MaterialTheme.colorScheme.primary.copy(alpha=if (enabled) 1f else .35f)
    Box(modifier.size(52.dp,48.dp).toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange)) {
        PixelCanvas(Modifier.fillMaxSize()) {
            val top=12.dp.toPx(); val height=24.dp.toPx(); val inset=3.dp.toPx()
            drawRect(accent.copy(alpha=if (checked) .22f else .08f),Offset(0f,top),Size(size.width,height))
            drawRect(accent.copy(alpha=.6f),Offset(0f,top),Size(size.width,height),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val side=height-inset*2
            drawRect(if (checked) accent else accent.copy(alpha=.5f),
                Offset(if (checked) size.width-inset-side else inset,top+inset),Size(side,side))
        }
    }
}

internal fun DrawScope.drawPixelMeter(fraction: Float, color: Color) {
    val step=6.dp.toPx(); val gap=1.dp.toPx(); var x=0f
    while (x < size.width) {
        val width=minOf(step-gap,size.width-x)
        drawRect(color.copy(alpha=.15f),Offset(x,0f),Size(width,size.height))
        val fill=(size.width*fraction.coerceIn(0f,1f)-x).coerceIn(0f,width)
        if (fill > 0f) drawRect(color,Offset(x,0f),Size(fill,size.height))
        x+=step
    }
}

@Composable internal fun PixelMeter(fraction: Float, color: Color, modifier: Modifier=Modifier) {
    Canvas(modifier.progressSemantics(fraction.coerceIn(0f,1f))) { drawPixelMeter(fraction,color) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpaceSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier=Modifier,
    enabled: Boolean=true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int=0,
    onValueChangeFinished: (() -> Unit)?=null) {
    if (!LocalRetroUi.current) {
        Slider(value,onValueChange,modifier,enabled, valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished)
        return
    }
    val color=MaterialTheme.colorScheme.primary.copy(alpha=if (enabled) 1f else .35f)
    Slider(value,onValueChange,modifier,enabled, valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished,
        thumb={ Canvas(Modifier.size(12.dp,20.dp)) { drawRect(color) } },
        track={ Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawPixelMeter((value-valueRange.start)/(valueRange.endInclusive-valueRange.start),color)
        } })
}
