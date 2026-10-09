package com.xekep.space.ui.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.sim.*
import com.xekep.space.ui.theme.SpaceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SolidFlightUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun screenshot(name: String) {
        File(context.externalCacheDir,name).outputStream().use { assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun solidModelsShowTheirNoseSidesUndersideAndRealNozzlesFromSixAngles() {
        compose.mainClock.autoAdvance=false
        val model=mutableStateOf(Triple(BodyKind.Ship,false,false))
        compose.setContent { SpaceTheme {
            Canvas(Modifier.fillMaxSize().background(Color(0xFF060D1D)).testTag("mesh-gallery")) {
                val viewport=IntSize(size.width.toInt(),size.height.toInt())
                val (kind,heavy,guardian)=model.value
                val poses=listOf(0.0 to 0.0,.78 to 0.0,-.78 to 0.0,0.0 to .87,0.0 to -.87,.65 to .80)
                val labels=listOf("Level","Nose up","Nose down","Roll right","Roll left","Combined")
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.LTGRAY; textSize=13*density; textAlign=android.graphics.Paint.Align.CENTER }
                poses.forEachIndexed { index,(pitch,roll) ->
                    val cell=Offset(size.width*((index%3+.5f)/3),size.height*((index/3+.5f)/2))
                    val camera=Vec2((size.width/2-cell.x).toDouble(),(size.height/2-cell.y).toDouble())
                    val body=CelestialBody(1,Vec2.Zero,Vec2(0.0,-240.0),if (heavy) heavyHullMass(kind) else if (kind == BodyKind.Ship) 24.0 else 12.0,
                        8f,Color(0xFF80FFDF),kind,heading=Vec2(0.0,-1.0),pitch=pitch,roll=roll,pilotTargetSpeed=240.0,pilotThrottle=.6,
                        shipClass=if (guardian) ShipClass.Guardian else ShipClass.Interceptor,trail=(12 downTo 0).map { Vec2(0.0,it*35.0) })
                    val r=vehicleRenderRadius(body,1f,density,true,2.4f)
                    drawTrail(body,viewport,camera,1f,vehicleRadius=r,maxLengthDp=140f)
                    drawBody(body,viewport,camera,1f,piloted=true,largeVehicleIcons=true,pilotVisualZoom=2.4f)
                    drawContext.canvas.nativeCanvas.drawText(labels[index],cell.x,cell.y-r*1.75f,paint)
                }
            }
        } }
        for (type in listOf(Triple(BodyKind.Ship,false,false),Triple(BodyKind.Ship,false,true),Triple(BodyKind.Ship,true,false),Triple(BodyKind.Rocket,false,false),Triple(BodyKind.Rocket,true,false))) {
            compose.runOnIdle { model.value=type }; compose.mainClock.advanceTimeBy(48)
            screenshot("solid-flight-${type.first.name}-${if (type.second) "heavy" else if (type.third) "guardian" else "standard"}.png")
        }
    }
    @Test fun spacecraftAndTheirWakesAppearAboveAStarAndAreOccludedBelowIt() {
        compose.mainClock.autoAdvance=false
        val height=mutableStateOf<Double?>(null)
        var viewport=IntSize.Zero
        val star=CelestialBody(2,Vec2.Zero,Vec2.Zero,1000.0,120f,Color(0xFFFFD166),BodyKind.Star)
        val template=CelestialBody(1,Vec2.Zero,Vec2(0.0,-200.0),24.0,6f,Color.Cyan,BodyKind.Ship,
            heading=Vec2(0.0,-1.0),pilotTargetSpeed=200.0,pilotThrottle=.4,trail=listOf(Vec2(0.0,300.0),Vec2(0.0,150.0),Vec2.Zero))
        compose.setContent { SpaceTheme {
            Canvas(Modifier.fillMaxSize().background(Color(0xFF060D1D)).testTag("overflight")) {
                viewport=IntSize(size.width.toInt(),size.height.toInt())
                val craft=height.value?.let { template.copy(flightHeight=it) }
                val bodies=listOfNotNull(star,craft)
                val interpolation=SandboxInterpolation()
                drawWorldBodies(bodies,viewport,SpaceCamera(),0.0,craft?.id,true,interpolation,false,pilotVisualZoom=1f,
                    vehicleTrail={ drawTrail(it,viewport,Vec2.Zero,1f,vehicleRadius=vehicleRenderRadius(it,1f,density,true,1f)) })
            }
        } }
        compose.mainClock.advanceTimeBy(48)
        val reference=compose.onRoot().captureToImage().asAndroidBitmap()
        for (z in listOf(-80.0,80.0)) {
            compose.runOnIdle { height.value=z }; compose.mainClock.advanceTimeBy(48)
            val rendered=compose.onRoot().captureToImage().asAndroidBitmap()
            val point=worldToScreen(flightRenderPosition(template.copy(flightHeight=z),Vec2.Zero,viewport,1f,0.0),viewport,Vec2.Zero,1f)
            val x=point.x.toInt(); val y=point.y.toInt()
            if (z < 0) assertEquals(reference.getPixel(x,y),rendered.getPixel(x,y))
            else assertNotEquals(reference.getPixel(x,y),rendered.getPixel(x,y))
            screenshot("solid-flight-star-${if (z > 0) "above" else "below"}.png")
        }
    }
}
