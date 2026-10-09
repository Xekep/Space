package com.xekep.space.ui.space

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.MainActivity
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Exercise real Activity locales: Dialog windows use their Activity's resource configuration. */
class RetroLanguageUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private var oldRetro: Boolean?=null
    private var oldChosen: Boolean?=null
    private var oldLocales=LocaleListCompat.getEmptyLocaleList()
    private var scenario: ActivityScenario<MainActivity>?=null

    @Before fun openRetroMenu() {
        val options=context.getSharedPreferences("space_options",0)
        val language=context.getSharedPreferences("space_language",0)
        oldRetro=if (options.contains("retroConsole")) options.getBoolean("retroConsole",false) else null
        oldChosen=if (language.contains("chosen")) language.getBoolean("chosen",false) else null
        oldLocales=if (Build.VERSION.SDK_INT >= 33)
            LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
            else AppCompatDelegate.getApplicationLocales()
        options.edit().putBoolean("retroConsole",true).commit()
        language.edit().putBoolean("chosen",true).commit()
        scenario=ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity {
            ViewModelProvider(it)[SpaceViewModel::class.java].game.apply {
                startSandbox(SandboxPresetKind.SolarSystem); toggleSandboxPause(); openMenu()
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("menu-language").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun restoreUserOptionsAndLanguage() {
        try {
            scenario?.onActivity { AppCompatDelegate.setApplicationLocales(oldLocales) }
            instrumentation.waitForIdleSync()
        } finally {
            scenario?.close()
            context.getSharedPreferences("space_options",0).edit().apply {
                if (oldRetro == null) remove("retroConsole") else putBoolean("retroConsole",oldRetro!!)
            }.commit()
            context.getSharedPreferences("space_language",0).edit().apply {
                if (oldChosen == null) remove("chosen") else putBoolean("chosen",oldChosen!!)
            }.commit()
        }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(200)
        instrumentation.waitForIdleSync()
        File(context.externalCacheDir,name).outputStream().use {
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
    private fun assertReadable(label: String, scroll: Boolean=false) {
        val layouts=mutableListOf<TextLayoutResult>()
        val node=compose.onNodeWithText(label,useUnmergedTree=true)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Clipped height: $label",layout.didOverflowHeight)
            assertEquals("Missing end of label: $label",label.length,layout.getLineEnd(layout.lineCount-1))
            for (line in 0 until layout.lineCount) {
                assertFalse("Truncated label: $label",layout.isLineEllipsized(line))
                // A centered paragraph can be wider than its intrinsic Text node; check its available slot.
                assertTrue("Clipped width: $label",layout.getLineRight(line)-layout.getLineLeft(line) <=
                    layout.layoutInput.constraints.maxWidth+1f)
            }
        }
    }

    @Test fun realMenuAndSettingsStayReadableInAllFiveLanguages() {
        for (tag in listOf("en","ru","fr","de","zh-Hans")) {
            compose.onNodeWithTag("menu-language").performClick()
            compose.onNodeWithTag("language-$tag").performScrollTo().performClick()
            compose.waitUntil(10000) {
                var localized=false
                scenario!!.onActivity { localized=it.resources.configuration.locales[0].language == Locale.forLanguageTag(tag).language }
                localized && compose.onAllNodesWithTag("menu-primary").fetchSemanticsNodes().isNotEmpty()
            }
            val translated=context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
            listOf(R.string.solar,R.string.binary,R.string.classic_orbits,R.string.random_systems_short,
                R.string.system_galaxy_short,R.string.resume_game).forEach { assertReadable(translated.getString(it)) }
            compose.onNodeWithTag("retro-screen-overlay").assertExists()
            shot("retro-ui-menu-$tag.png")
            compose.onNodeWithTag("open-settings").performClick()
            compose.onNodeWithTag("settings-panel").assertIsDisplayed()
            shot("retro-ui-settings-$tag.png")
            listOf(R.string.flight_controls,R.string.retro_console,R.string.large_vehicle_icons)
                .forEach { assertReadable(translated.getString(it),scroll=true) }
            compose.onNodeWithTag("retro-console-switch").assertIsOn()
            shot("retro-ui-settings-bottom-$tag.png")
            compose.onNodeWithTag("close-menu-panel").performClick()
            compose.onNodeWithTag("settings-panel").assertDoesNotExist()
        }
    }
}
