package com.xekep.space.ui.space

import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test

class SolarPresentationTest {
    @Test fun catalogueBodiesKeepTexturesReadableAndPhysicalSizesAtCloseZoom() {
        val bodies=SimulationEngine.sandboxPreset().bodies
        val earth=bodies.first { it.solar == SolarBody.Earth }
        val saturn=bodies.first { it.solar == SolarBody.Saturn }
        assertEquals(6f*3f,bodyScreenRadius(earth,.2f,3f),1e-5f)
        assertEquals(earth.radius*1000,bodyScreenRadius(earth,1000f,3f),1e-5f)
        assertEquals(saturn.radius*1000,bodyScreenRadius(saturn,1000f,3f),1e-5f)
        assertEquals(SolarSystem.RING_GRAINS,visibleSolarBodies(bodies,1000f,3f).count { it.orbitalDetail == OrbitalDetail.RingGrain })
        assertEquals(0,visibleSolarBodies(bodies,.001f,3f).count { it.orbitalDetail != null })
    }
    @Test fun nominalOrbitsStayVisibleAndStrongSingleBodyEditsHideOnlyItsGuide() {
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        game.update(.1)
        assertTrue(game.hiddenSolarOrbits.isEmpty())
        val earth=game.bodies.first { it.solar == SolarBody.Earth }
        game.selectBody(earth.id); game.editSelected(earth.mass,earth.velocity*1.6); game.update(.1)
        val moon=game.bodies.first { it.solar == SolarBody.Moon }
        assertEquals(setOf(earth.id,moon.id),game.hiddenSolarOrbits)
        game.editSelected(earth.mass,earth.velocity); game.update(.1)
        assertEquals(setOf(earth.id,moon.id),game.hiddenSolarOrbits)
    }
    @Test fun captionsFadeAtOneMinuteOfViewingAndMenuDoesNotConsumeTheTimer() {
        val game=SpaceGameState().apply { startSandbox(); toggleSandboxPause() }
        repeat(550) { game.update(.1) }
        assertEquals(1f,solarLabelAlpha(game.presentationAge),1e-5f)
        game.openMenu(); repeat(100) { game.update(.1) }; assertEquals(55.0,game.presentationAge,1e-6)
        game.closeMenu(); repeat(25) { game.update(.1) }
        assertEquals(.5f,solarLabelAlpha(game.presentationAge),1e-5f)
        repeat(26) { game.update(.1) }; assertEquals(0f,solarLabelAlpha(game.presentationAge),0f)
        game.startSandbox(); assertEquals(0.0,game.presentationAge,0.0)
    }
}
