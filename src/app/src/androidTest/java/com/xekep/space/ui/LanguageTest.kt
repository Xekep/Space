package com.xekep.space.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xekep.space.MainActivity
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.ui.space.SpaceGameState
import com.xekep.space.ui.space.SpaceViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.Locale

class LanguageTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("space_language", Context.MODE_PRIVATE)
    private var priorChosen: Boolean? = null
    private var priorLocales = LocaleListCompat.getEmptyLocaleList()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun firstLaunch() {
        priorChosen = if (preferences.contains("chosen")) preferences.getBoolean("chosen", false) else null
        priorLocales = if (Build.VERSION.SDK_INT >= 33)
            LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
            else AppCompatDelegate.getApplicationLocales()
        preferences.edit().remove("chosen").commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun restorePreferences() {
        scenario?.onActivity { AppCompatDelegate.setApplicationLocales(priorLocales) }
        instrumentation.waitForIdleSync()
        scenario?.close()
        preferences.edit().apply {
            if (priorChosen == null) remove("chosen") else putBoolean("chosen", priorChosen!!)
        }.commit()
    }

    private fun waitForMenu() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("menu-language").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        File(context.externalCacheDir, name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun firstLaunchRequiresAChoiceAndRetainsFrenchAfterRecreation() {
        compose.onNodeWithTag("first-language-screen").assertIsDisplayed()
        compose.onNodeWithTag("menu-language").assertDoesNotExist()
        AppLanguage.entries.forEach { compose.onNodeWithTag("language-${it.tag}").performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("language-en").performScrollTo()
        screenshot("first-language.png")
        compose.onNodeWithTag("language-fr").performScrollTo().performClick()
        waitForMenu()
        compose.onNodeWithTag("mode-Sandbox").assertTextContains("Bac à sable")
        assertTrue(LanguageSettings(context).hasChosen)
        scenario!!.recreate()
        waitForMenu()
        compose.onNodeWithTag("first-language-screen").assertDoesNotExist()
        compose.onNodeWithTag("menu-language").assertTextContains("Français", substring = true)
        scenario!!.onActivity { assertEquals("fr", it.resources.configuration.locales[0].language) }
    }

    @Test fun menuSwitchesAllFiveLanguagesAndKeepsTheUniverseAndMenuChoice() {
        compose.onNodeWithTag("language-en").performScrollTo().performClick()
        waitForMenu()
        lateinit var game: SpaceGameState
        scenario!!.onActivity {
            game = ViewModelProvider(it)[SpaceViewModel::class.java].game
            game.startSandbox(SandboxPresetKind.ClassicOrbits, "My universe")
            game.toggleSandboxPause()
            game.openMenu()
        }
        val bodies = game.bodies
        compose.onNodeWithTag("preset-BinaryStars").performScrollTo().performClick()
        for ((language, name) in listOf(AppLanguage.French to "Bac à sable", AppLanguage.German to "Sandkasten",
            AppLanguage.Chinese to "沙盒", AppLanguage.Russian to "Песочница", AppLanguage.English to "Sandbox")) {
            compose.onNodeWithTag("preset-BinaryStars").performScrollTo().performClick()
            compose.onNodeWithTag("menu-language").assertIsDisplayed().performClick()
            compose.onNodeWithTag("language-${language.tag}").performScrollTo().performClick()
            waitForMenu()
            compose.waitUntil(10000) {
                var matches = false
                scenario!!.onActivity { matches = it.resources.configuration.locales[0].language == Locale.forLanguageTag(language.tag).language }
                matches
            }
            compose.onNodeWithTag("mode-Sandbox").performScrollTo().assertTextContains(name)
            compose.onNodeWithTag("preset-BinaryStars").assertIsSelected()
            scenario!!.onActivity {
                assertSame(game, ViewModelProvider(it)[SpaceViewModel::class.java].game)
                assertEquals(bodies, game.bodies)
                assertEquals("My universe", game.sandbox!!.name)
                if (Build.VERSION.SDK_INT >= 33) assertEquals(language.tag,
                    it.getSystemService(LocaleManager::class.java).applicationLocales[0].toLanguageTag())
            }
            screenshot("language-menu-${language.tag}.png")
            compose.onNodeWithTag("menu-primary").performClick()
            compose.onNodeWithTag("space-scene").assertIsDisplayed()
            scenario!!.onActivity { game.openMenu() }
        }
    }

    @Test fun translatedResourcesFormatAndLabelPlanetsInEveryLanguage() {
        val planetNames = listOf("Earth", "Земля", "Terre", "Erde", "地球")
        for ((index, language) in AppLanguage.entries.withIndex()) {
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language.tag))
            val translated = context.createConfigurationContext(configuration)
            assertEquals(planetNames[index], translated.getString(R.string.earth))
            for (field in R.string::class.java.fields) {
                val id = field.getInt(null)
                val raw = translated.getString(id)
                if (raw.contains(Regex("%[123]\\$[ds]"))) {
                    val args = (1..3).map { n -> if (raw.contains("%${n}\$s")) "1.5" else 12 }.toTypedArray()
                    assertTrue(translated.getString(id, *args).isNotBlank())
                }
            }
        }
    }
}
