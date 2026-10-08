package com.xekep.space.storage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xekep.space.input.FlightControlMode
import com.xekep.space.sim.ShakeMode

class GameOptions(context: Context) {
    private val preferences = context.getSharedPreferences("space_options", Context.MODE_PRIVATE)
    var sound by mutableStateOf(preferences.getBoolean("sound", true))
    var music by mutableStateOf(preferences.getBoolean("music", true))
    var vibration by mutableStateOf(preferences.getBoolean("vibration", true))
    var flightControl by mutableStateOf(runCatching {
        FlightControlMode.valueOf(preferences.getString("flightControl",null) ?: "Tilt")
    }.getOrDefault(FlightControlMode.Tilt))
    var tiltSensitivity by mutableStateOf(preferences.getFloat("tiltSensitivity",1f)
        .takeIf { it.isFinite() }?.coerceIn(.5f,1.75f) ?: 1f)
    var largeVehicleIcons by mutableStateOf(preferences.getBoolean("largeVehicleIcons", true))
    var shakeMode by mutableStateOf(runCatching { ShakeMode.valueOf(preferences.getString("shakeMode",null) ?: "") }
        .getOrElse { if (preferences.getBoolean("shake",false)) ShakeMode.Inertial else ShakeMode.Off })
    var shakeIntensity by mutableStateOf(preferences.getFloat("shakeIntensity",1f).takeIf { it.isFinite() }?.coerceIn(.25f,2.5f) ?: 1f)
    var shake: Boolean
        get() = shakeMode != ShakeMode.Off
        set(value) { shakeMode = if (value) ShakeMode.Inertial else ShakeMode.Off }
    var reducedFlashes by mutableStateOf(preferences.getBoolean("reducedFlashes", false))
    fun save() { preferences.edit().putBoolean("sound", sound).putBoolean("music", music).putBoolean("vibration", vibration)
        .remove("motionControl").putBoolean("reducedFlashes", reducedFlashes).putBoolean("shake", shake)
        .putString("flightControl",flightControl.name)
        .putFloat("tiltSensitivity",tiltSensitivity.takeIf { it.isFinite() }?.coerceIn(.5f,1.75f) ?: 1f)
        .putBoolean("largeVehicleIcons", largeVehicleIcons)
        .putString("shakeMode",shakeMode.name).putFloat("shakeIntensity",shakeIntensity.takeIf { it.isFinite() }?.coerceIn(.25f,2.5f) ?: 1f).apply() }
}
