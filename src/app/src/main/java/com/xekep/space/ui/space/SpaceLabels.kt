package com.xekep.space.ui.space

import androidx.annotation.StringRes
import com.xekep.space.R
import com.xekep.space.sim.SandboxPresetKind
import com.xekep.space.sim.hullClass
import com.xekep.space.sim.BodyKind

@StringRes
fun BodyKind.labelId(): Int = when (this) {
    BodyKind.Ship -> R.string.spawn_ship
    BodyKind.Rocket -> R.string.spawn_rocket
    BodyKind.Core, BodyKind.Star -> R.string.spawn_star
    BodyKind.BlackHole -> R.string.spawn_black_hole
    BodyKind.ArcadePlanet -> R.string.arcade_planet
    BodyKind.Convoy -> R.string.convoy
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
    SandboxPresetKind.SystemGalaxy -> R.string.system_galaxy_name
    SandboxPresetKind.Empty -> R.string.empty_space
}

@StringRes
fun SandboxPresetKind.descriptionId(): Int = when (this) {
    SandboxPresetKind.SolarSystem -> R.string.solar_description
    SandboxPresetKind.BinaryStars -> R.string.binary_description
    SandboxPresetKind.ClassicOrbits -> R.string.classic_orbits_description
    SandboxPresetKind.RandomSystems -> R.string.random_systems_description
    SandboxPresetKind.SystemGalaxy -> R.string.system_galaxy_description
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
    null -> when {
        orbitalDetail == com.xekep.space.sim.OrbitalDetail.ArtificialSatellite -> R.string.artificial_satellite
        orbitalDetail == com.xekep.space.sim.OrbitalDetail.RingGrain -> R.string.ring_grain
        orbitParentId != null && galaxySystemId != null -> if (orbitParentId == galaxySystemId) R.string.galaxy_planet else R.string.galaxy_moon
        hullClass == com.xekep.space.sim.VehicleHullClass.Heavy && kind == BodyKind.Ship ->
            if (shipClass == com.xekep.space.sim.ShipClass.Guardian) R.string.spawn_heavy_guardian else R.string.spawn_heavy_ship
        hullClass == com.xekep.space.sim.VehicleHullClass.Heavy && kind == BodyKind.Rocket -> R.string.spawn_heavy_rocket
        kind == BodyKind.Ship && shipClass == com.xekep.space.sim.ShipClass.Guardian -> R.string.spawn_guardian
        else -> kind.labelId()
    }
}


@StringRes
fun SpaceGameState.spawnLabelId(): Int = if (mode == AppMode.Arcade && spawnKind == BodyKind.Ship &&
    arcadeShipClass == com.xekep.space.sim.ShipClass.Guardian) R.string.spawn_guardian else
    if (spawnKind == BodyKind.Ambient) R.string.spawn_body_short else spawnKind.labelId()

@StringRes
fun ArcadeUpgrade.labelId(): Int = when (this) {
    ArcadeUpgrade.Fleet -> R.string.upgrade_fleet
    ArcadeUpgrade.Engines -> R.string.upgrade_engines
    ArcadeUpgrade.Guns -> R.string.upgrade_guns
    ArcadeUpgrade.Reactor -> R.string.upgrade_reactor
    ArcadeUpgrade.Repair -> R.string.upgrade_repair
}
@StringRes
fun ArcadeUpgrade.descriptionId(): Int = when (this) {
    ArcadeUpgrade.Fleet -> R.string.upgrade_fleet_help
    ArcadeUpgrade.Engines -> R.string.upgrade_engines_help
    ArcadeUpgrade.Guns -> R.string.upgrade_guns_help
    ArcadeUpgrade.Reactor -> R.string.upgrade_reactor_help
    ArcadeUpgrade.Repair -> R.string.upgrade_repair_help
}
