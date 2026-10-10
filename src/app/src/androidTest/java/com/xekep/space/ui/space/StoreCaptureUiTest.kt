package com.xekep.space.ui.space

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.appcompat.content.res.AppCompatResources
import com.xekep.space.R
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.ui.theme.SpaceTheme
import com.xekep.space.sim.*
import androidx.compose.ui.graphics.Color
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale

/** Prepared in-engine showcase scenes, not evidence of human playthrough or phone performance. */
class StoreCaptureUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun captureActualUiInTwoStoreLanguages() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeCapture") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences="store_capture_"+System.nanoTime()
        context.getSharedPreferences(preferences,0).edit().putString("flightControl","Joystick").putBoolean("music",false).putBoolean("sound",false).putBoolean("learningOffered",true).commit()
        try {
            val assets=File(context.externalCacheDir,"store-capture/assets").apply { mkdirs() }
            fun asset(name: String,width: Int,height: Int,draw: (Canvas)->Unit) {
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                draw(Canvas(bitmap));File(assets,name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
            }
            val icon=requireNotNull(AppCompatResources.getDrawable(context,R.drawable.ic_launcher_foreground))
            asset("icon-512.png",512,512) { canvas -> canvas.drawColor(android.graphics.Color.rgb(5,9,19));icon.setBounds(0,0,512,512);icon.draw(canvas) }
            asset("feature-1024x500.png",1024,500) { canvas ->
                val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader=LinearGradient(0f,0f,1024f,500f,intArrayOf(0xff070c18.toInt(),0xff163649.toInt()),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,1024f,500f,paint);paint.shader=null
                paint.color=0xff8197a8.toInt();repeat(80) { index -> canvas.drawCircle(((index*137)%1024).toFloat(),((index*79)%500).toFloat(),if(index%9==0)2f else 1f,paint) }
                icon.setBounds(10,0,510,500);icon.draw(canvas)
                paint.color=0xffeeeeee.toInt();paint.textSize=94f;canvas.drawText("Space",550f,270f,paint)
                paint.color=0xffffce5b.toInt();paint.textSize=25f;canvas.drawText("Gravity & Fleet",554f,315f,paint)
            }
            val game=SpaceGameState();var locale by mutableStateOf("en")
            compose.mainClock.autoAdvance=false
            compose.setContent {
                val host=LocalContext.current
                val localized=remember(locale) { host.createConfigurationContext(Configuration(host.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) }) }
                val wrapper=remember(localized) { object: ContextWrapper(host) {
                    override fun getResources()=localized.resources
                    override fun getSharedPreferences(key: String,mode: Int): SharedPreferences=context.getSharedPreferences(preferences,mode)
                } }
                CompositionLocalProvider(LocalContext provides wrapper) { SpaceTheme { SpaceSceneRoot(game) } }
            }
            compose.mainClock.advanceTimeBy(64)
            fun capture(name: String) {
                compose.mainClock.advanceTimeBy(96);compose.waitForIdle()
                val directory=File(context.externalCacheDir,"store-capture/$locale").apply { mkdirs() }
                File(directory,"$name.png").outputStream().use { stream -> assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream)) }
            }
            for (language in listOf("en","ru")) {
                compose.runOnIdle { locale=language;game.openMenu() };capture("01-menu")
                compose.runOnIdle {
                    game.startArcade()
                    val snapshot=game.recoverySnapshot(0)!!;val run=snapshot.arcade!!;val center=run.camera.center
                    val threats=listOf(-180.0,230.0).mapIndexed { index,x -> CelestialBody(SimulationEngine.newBodyId(),center+Vec2(x,-500.0-index*140),Vec2(-x*.2,100.0),100.0,16f,Color.Red,BodyKind.Meteor) }
                    game.restoreRecovery(snapshot.copy(arcade=run.copy(elapsed=300.0,bodies=run.bodies+threats)));game.closeMenu()
                    game.chooseSpawnKind(BodyKind.Ship)
                    for (x in listOf(-240.0,220.0)) { val point=center+Vec2(x,100.0);game.launch(TouchPreview(point,point+Vec2(0.0,100.0),0),0.0) }
                    game.chooseSpawnKind(BodyKind.Rocket);val point=center+Vec2(140.0,250.0);game.launch(TouchPreview(point,point+Vec2(0.0,120.0),0),0.0)
                    repeat(24) { game.update(1.0/60) }
                };capture("02-arcade")
                compose.runOnIdle { game.startSandbox(com.xekep.space.sim.SandboxPresetKind.SolarSystem,if(language == "ru") "Солнечная система" else "Solar System") };capture("03-solar")
                compose.runOnIdle { game.beginFlightPractice(true,if(language == "ru") "Практика полёта" else "Flight practice") };capture("04-pilot")
                compose.runOnIdle { game.startSandbox(com.xekep.space.sim.SandboxPresetKind.SystemGalaxy,if(language == "ru") "Галактика" else "Galaxy") };capture("05-galaxy")
            }
        } finally { context.deleteSharedPreferences(preferences) }
    }
}
