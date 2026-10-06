package com.xekep.space.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xekep.space.R
import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"), Russian("ru", "Русский"), French("fr", "Français"),
    German("de", "Deutsch"), Chinese("zh-Hans", "简体中文");

    companion object {
        fun forLocale(locale: Locale?): AppLanguage = entries.firstOrNull {
            Locale.forLanguageTag(it.tag).language == locale?.language
        } ?: English
    }
}

class LanguageSettings(context: Context) {
    private val preferences = context.getSharedPreferences("space_language", Context.MODE_PRIVATE)
    var hasChosen by mutableStateOf(preferences.getBoolean("chosen", false)); private set

    fun choose(language: AppLanguage) {
        preferences.edit().putBoolean("chosen", true).apply()
        hasChosen = true
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}

@Composable
private fun LanguageChoices(current: AppLanguage, onChoose: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { language ->
            val active = language == current
            Surface(onClick = { onChoose(language) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("language-${language.tag}")
                    .semantics { selected = active; role = Role.RadioButton },
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .14f))) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(language.nativeName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (active) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun FirstLaunchLanguagePicker(settings: LanguageSettings) {
    val current = AppLanguage.forLocale(LocalConfiguration.current.locales[0])
    Box(Modifier.fillMaxSize().background(Color(0xFF050B19)).safeDrawingPadding().padding(20.dp)
        .testTag("first-language-screen"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("SPACE", letterSpacing = 5.sp, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.choose_language), color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            LanguageChoices(current, settings::choose)
        }
    }
}

@Composable
fun LanguageMenuButton(modifier: Modifier = Modifier.fillMaxWidth()) {
    val context = LocalContext.current
    val settings = remember(context) { LanguageSettings(context) }
    val current = AppLanguage.forLocale(LocalConfiguration.current.locales[0])
    var choosing by remember { mutableStateOf(false) }
    TextButton(onClick = { choosing = true }, modifier = modifier.testTag("menu-language")) {
        Text(stringResource(R.string.language_setting, current.nativeName))
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.choose_language)) },
        text = {
            Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                LanguageChoices(current) { choosing = false; settings.choose(it) }
            }
        },
        confirmButton = {}, dismissButton = {
            TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) }
        })
}
