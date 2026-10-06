package com.xekep.space.ui.space

import androidx.annotation.StringRes
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.BodyKind

@StringRes
fun BodyKind.labelId(): Int = when (this) {
    BodyKind.Ship -> R.string.spawn_ship
    BodyKind.Rocket -> R.string.spawn_rocket
    BodyKind.Core -> R.string.spawn_star
    else -> R.string.spawn_body
}

@StringRes
fun BodyKind.spawnDescriptionId(): Int = when (this) {
    BodyKind.Ship -> R.string.spawn_ship_help
    BodyKind.Rocket -> R.string.spawn_rocket_help
    else -> R.string.spawn_body_help
}

@StringRes
fun AppMode.labelId(): Int = when (this) {
    AppMode.Arcade -> R.string.arcade
    AppMode.Sandbox -> R.string.sandbox
}

@StringRes
fun ArcadeDifficulty.labelId(): Int = when (this) {
    ArcadeDifficulty.Easy -> R.string.easy
    ArcadeDifficulty.Normal -> R.string.normal
    ArcadeDifficulty.Hard -> R.string.hard
}

@StringRes
fun SandboxPresetKind.labelId(): Int = when (this) {
    SandboxPresetKind.SolarSystem -> R.string.solar_system
    SandboxPresetKind.BinaryStars -> R.string.binary_stars
    SandboxPresetKind.Empty -> R.string.empty_space
}

@StringRes
fun SandboxPresetKind.descriptionId(): Int = when (this) {
    SandboxPresetKind.SolarSystem -> R.string.solar_description
    SandboxPresetKind.BinaryStars -> R.string.binary_description
    SandboxPresetKind.Empty -> R.string.empty_description
}
