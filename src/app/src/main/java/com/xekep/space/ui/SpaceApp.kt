package com.xekep.space.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.xekep.space.ui.space.SpaceSceneRoot

@Composable
fun SpaceApp() {
    val context = LocalContext.current
    val language = remember(context) { LanguageSettings(context) }
    if (language.hasChosen) SpaceSceneRoot() else FirstLaunchLanguagePicker(language)
}
