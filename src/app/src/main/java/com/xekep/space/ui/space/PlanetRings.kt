package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import kotlin.math.hypot

/** Continuous dust/ice belts, separate from physical moons and the orbiting tracer particles. */
internal data class PlanetRingBand(val innerKm: Double,val outerKm: Double,val opacity: Float)
internal data class PlanetRingSystem(val planet: SolarBody,val tint: Color,val bands: List<PlanetRingBand>)
internal fun planetRingSystem(planet: SolarBody?): PlanetRingSystem? = when (planet) {
    SolarBody.Jupiter -> PlanetRingSystem(planet,Color(0xFFB9A58F),listOf(
        PlanetRingBand(72000.0,122000.0,.025f),PlanetRingBand(122000.0,129000.0,.18f),
        PlanetRingBand(129000.0,181000.0,.018f),PlanetRingBand(181000.0,226000.0,.009f)))
    SolarBody.Saturn -> PlanetRingSystem(planet,Color(0xFFE9D6AD),listOf(
        PlanetRingBand(74658.0,91975.0,.23f),PlanetRingBand(91975.0,117507.0,.6f),
        PlanetRingBand(122340.0,136780.0,.43f)))
    else -> null
}
internal fun ringCoverage(parent: CelestialBody,scene: List<CelestialBody>): Float =
    if (parent.solar != SolarBody.Saturn || parent.solarOrbitScale == 1.0) 1f else
        (scene.count { it.orbitParentId == parent.id && it.orbitalDetail == OrbitalDetail.RingGrain }.toFloat()/SolarSystem.RING_GRAINS).coerceIn(0f,1f)

internal fun DrawScope.drawPlanetRings(parent: CelestialBody,scene: List<CelestialBody>,viewport: IntSize,
    camera: SpaceCamera,position: Vec2) {
    val system=planetRingSystem(parent.solar) ?: return
    val coverage=ringCoverage(parent,scene)
    if (coverage <= 0) return
    val center=worldToScreen(position,viewport,camera.center,camera.zoom)
    val physicalRadius=parent.radius*camera.zoom
    val displayRadius=bodyScreenRadius(parent,camera.zoom,density)
    // Overview belt follows the readable planetary symbol. Close up it matches the actual grains.
    val scale=maxOf(camera.zoom.toDouble(),displayRadius/parent.radius.toDouble())*AU_WORLD/AU_KM
    val flatten=.42f+.58f*((physicalRadius/density-4)/4).coerceIn(0f,1f)
    val extent=(system.bands.last().outerKm*scale).toFloat()
    if ((center-this.center).getDistance() > hypot(size.width,size.height)*.5f+extent) return
    system.bands.forEach { band ->
        val inner=(band.innerKm*scale).toFloat(); val outer=(band.outerKm*scale).toFloat()
        val middle=(inner+outer)*.5f
        drawOval(system.tint.copy(alpha=band.opacity*coverage),center-Offset(middle,middle*flatten),
            Size(middle*2,middle*2*flatten),style=Stroke(maxOf(.25f*density,outer-inner)))
        // Fine ringlets prevent a solid doughnut appearance; Cassini gap stays empty.
        if (system.planet == SolarBody.Saturn) repeat(5) { index ->
            val radius=inner+(outer-inner)*(index+.5f)/5
            drawOval(system.tint.copy(alpha=.07f*coverage),center-Offset(radius,radius*flatten),
                Size(radius*2,radius*2*flatten),style=Stroke(.35f*density))
        }
    }
}
