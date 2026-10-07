package com.xekep.space.ui.space

import androidx.annotation.StringRes
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.BodyKind

@StringRes
fun BodyKind.labelId(): Int = when (this) {
    BodyKind.Ship -> R.string.spawn_ship
    BodyKind.Rocket -> R.string.spawn_rocket
    BodyKind.Core, BodyKind.Star -> R.string.spawn_star
    BodyKind.BlackHole -> R.string.spawn_black_hole
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
    SandboxPresetKind.ClassicOrbits -> R.string.classic_orbits
    SandboxPresetKind.RandomSystems -> R.string.random_systems_name
    SandboxPresetKind.Empty -> R.string.empty_space
}

@StringRes
fun SandboxPresetKind.descriptionId(): Int = when (this) {
    SandboxPresetKind.SolarSystem -> R.string.solar_description
    SandboxPresetKind.BinaryStars -> R.string.binary_description
    SandboxPresetKind.ClassicOrbits -> R.string.classic_orbits_description
    SandboxPresetKind.RandomSystems -> R.string.random_systems_description
    SandboxPresetKind.Empty -> R.string.empty_description
}

@StringRes
fun com.xekep.space.sim.CelestialBody.labelId(): Int = when (solar) {
    com.xekep.space.sim.SolarBody.Sun -> R.string.sun
    com.xekep.space.sim.SolarBody.Mercury -> R.string.mercury
    com.xekep.space.sim.SolarBody.Venus -> R.string.venus
    com.xekep.space.sim.SolarBody.Earth -> R.string.earth
    com.xekep.space.sim.SolarBody.Mars -> R.string.mars
    com.xekep.space.sim.SolarBody.Jupiter -> R.string.jupiter
    com.xekep.space.sim.SolarBody.Saturn -> R.string.saturn
    com.xekep.space.sim.SolarBody.Uranus -> R.string.uranus
    com.xekep.space.sim.SolarBody.Neptune -> R.string.neptune
    com.xekep.space.sim.SolarBody.Pluto -> R.string.pluto
    com.xekep.space.sim.SolarBody.Moon -> R.string.moon
    com.xekep.space.sim.SolarBody.Io -> R.string.io
    com.xekep.space.sim.SolarBody.Europa -> R.string.europa
    com.xekep.space.sim.SolarBody.Ganymede -> R.string.ganymede
    com.xekep.space.sim.SolarBody.Callisto -> R.string.callisto
    com.xekep.space.sim.SolarBody.Titan -> R.string.titan
    com.xekep.space.sim.SolarBody.Triton -> R.string.triton
    null -> kind.labelId()
}
