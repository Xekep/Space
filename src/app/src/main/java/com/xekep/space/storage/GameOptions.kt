package com.xekep.space.storage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class GameOptions(context: Context) {
    private val preferences = context.getSharedPreferences("space_options", Context.MODE_PRIVATE)
    var sound by mutableStateOf(preferences.getBoolean("sound", true))
    var music by mutableStateOf(preferences.getBoolean("music", true))
    var vibration by mutableStateOf(preferences.getBoolean("vibration", true))
    var shake by mutableStateOf(preferences.getBoolean("shake", false))
    var reducedFlashes by mutableStateOf(preferences.getBoolean("reducedFlashes", false))
    fun save() { preferences.edit().putBoolean("sound", sound).putBoolean("music", music).putBoolean("vibration", vibration)
        .putBoolean("reducedFlashes", reducedFlashes).putBoolean("shake", shake).apply() }
}
