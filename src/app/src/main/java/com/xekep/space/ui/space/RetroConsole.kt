package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One reusable low-resolution framebuffer. World coordinates and hit testing stay unchanged. */
internal class RetroRenderer(private val pixelSize: Dp = 3.dp) {
    private var image: ImageBitmap?=null
    private var canvas: androidx.compose.ui.graphics.Canvas?=null
    private val scope=CanvasDrawScope()
    private val tone=ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.85f) })

    internal val bufferSize: IntSize get()=image?.let { IntSize(it.width,it.height) } ?: IntSize.Zero

    fun draw(destination: DrawScope, content: DrawScope.() -> Unit) = with(destination) {
        if (size.width <= 0f || size.height <= 0f) return@with
        val pixel=pixelSize.toPx().roundToInt().coerceAtLeast(1)
        val width=ceil(size.width/pixel).toInt().coerceAtLeast(1)
        val height=ceil(size.height/pixel).toInt().coerceAtLeast(1)
        val frame=image?.takeIf { it.width == width && it.height == height } ?: ImageBitmap(width,height).also {
            image=it; canvas=Canvas(it)
        }
        frame.asAndroidBitmap().eraseColor(android.graphics.Color.TRANSPARENT)
        val target=checkNotNull(canvas)
        target.save()
        try {
            target.scale(width/size.width,height/size.height)
            scope.draw(this,layoutDirection,target,size,content)
        } finally { target.restore() }
        drawImage(frame,dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),
            filterQuality=FilterQuality.None,colorFilter=tone)
    }
}

internal fun DrawScope.drawRetroFrame(renderer: RetroRenderer?, content: DrawScope.() -> Unit) {
    if (renderer == null) content() else renderer.draw(this,content)
}

/** Static CRT texture, never flashing. No input handlers: gestures reach the scene and HUD. */
@Composable
internal fun RetroScreenOverlay() {
    Canvas(Modifier.fillMaxSize().testTag("retro-screen-overlay")) {
        val step=6.dp.toPx()
        var y=step*.5f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha=.10f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
            y+=step
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.22f)),
            center,size.maxDimension*.65f))
    }
}
