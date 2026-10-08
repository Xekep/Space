package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class BarnesHutTest {
    private fun scene(seed: Int = 17): List<CelestialBody> { var id=0L; return RandomSystems.create(Random(seed)) { ++id } }
    private fun force(bodies: List<CelestialBody>,theta: Double,conserve: Boolean = true): Pair<DoubleArray,Int> {
        val state=DoubleArray(bodies.size*4); val masses=DoubleArray(bodies.size); val soft=DoubleArray(bodies.size)
        bodies.forEachIndexed { i,b ->
            state[i*4]=b.position.x; state[i*4+1]=b.position.y
            state[i*4+2]=b.velocity.x; state[i*4+3]=b.velocity.y
            masses[i]=b.gravityMass; soft[i]=b.gravitySoftening
        }
        val out=DoubleArray(bodies.size*2); val tree=BarnesHutGravity(theta)
        tree.compute(state,masses,soft,out,conserveMomentum=conserve)
        return out to tree.interactions
    }
    private fun direct(bodies: List<CelestialBody>): DoubleArray {
        val out=DoubleArray(bodies.size*2)
        for (i in bodies.indices) for (j in i+1 until bodies.size) {
            val delta=bodies[j].position-bodies[i].position
            val soft=forceSoftening(bodies[i],bodies[j]); val square=delta.x*delta.x+delta.y*delta.y+soft*soft
            val factor=400.0/(square*sqrt(square))
            out[i*2]+=delta.x*factor*bodies[j].gravityMass; out[i*2+1]+=delta.y*factor*bodies[j].gravityMass
            out[j*2]-=delta.x*factor*bodies[i].gravityMass; out[j*2+1]-=delta.y*factor*bodies[i].gravityMass
        }
        return out
    }
    @Test fun zeroOpeningAngleMatchesExactMixedScaleForcesAndCoincidentBodies() {
        val bodies=List(180) { i -> CelestialBody(i.toLong(),if (i < 20) Vec2.Zero else Vec2(i*3.0,i%11*7.0),Vec2.Zero,
            1.0+i%7,1f,Color.Cyan,physicalScale=i%3 == 0,galaxyParticle=i%3 == 1) }
        val actual=force(bodies,0.0).first; val exact=direct(bodies)
        for (i in exact.indices) assertEquals("force[$i]",exact[i],actual[i],max(1e-7,abs(exact[i])*1e-10))
    }
    @Test fun fiveHundredBodyForcesRemainAccurateWhileUsingFarFewerInteractions() {
        for (seed in listOf(17,53,99)) {
            val bodies=scene(seed); val exact=direct(bodies); val (actual,cost)=force(bodies,.5)
            val errors=bodies.indices.map { i -> hypot(actual[i*2]-exact[i*2],actual[i*2+1]-exact[i*2+1])/
                hypot(exact[i*2],exact[i*2+1]).coerceAtLeast(1e-8) }.sorted()
            val rms=sqrt(errors.sumOf { it*it }/errors.size)
            val raw=force(bodies,.5,false).first
            val residual=bodies.indices.fold(Vec2.Zero) { sum,i -> sum+Vec2(raw[i*2],raw[i*2+1])*bodies[i].mass }/bodies.sumOf { it.mass }
            println("Uncorrected common acceleration=$residual")
            println("BH seed=$seed rms=$rms p95=${errors[475]} interactions=$cost exact=${500*499}")
            assertTrue("RMS force error: $rms",rms < .015)
            assertTrue("95th percentile error: ${errors[475]}",errors[475] < .025)
            assertTrue("Interactions: $cost",cost < 500*499*.4)
        }
    }
    @Test fun randomGeneratorCreatesAStellarGalaxyAndExactlyFiveHundredBodies() {
        for (seed in listOf(17,53,99)) {
            val bodies=scene(seed)
            assertEquals(500,bodies.size); assertEquals(500,bodies.map { it.id }.distinct().size)
            assertTrue(bodies.count { it.kind == BodyKind.Star } > 400); assertEquals(1,bodies.count { it.kind == BodyKind.BlackHole })
            assertTrue(bodies.all { it.mass > 0 && it.radius > 0 && it.position.x.isFinite() && it.velocity.y.isFinite() })
            for (i in bodies.indices) for (j in i+1 until bodies.size)
                assertTrue("Initial overlap: ${bodies[i].id}, ${bodies[j].id}",(bodies[i].position-bodies[j].position).magnitude() > bodies[i].radius+bodies[j].radius)
        }
        assertEquals(scene(17),scene(17)); assertNotEquals(scene(17),scene(53))
    }
    @Test fun largeSceneRetainsMomentumAndTracksExactIntegrationOverTwoSeconds() {
        val initial=scene(); var approximate=initial; var exact=initial
        val initialMomentum=initial.fold(Vec2.Zero) { p,b -> p+b.velocity*b.mass }
        val energy=SimulationEngine.totalEnergy(initial)
        repeat(120) {
            approximate=SimulationEngine.stepSandbox(approximate,1.0/60,energy).bodies
            exact=NumericIntegrator.advance(exact,1.0/60,BarnesHutGravity.stepLimit(exact),approximateGravity=false)
        }
        val momentum=approximate.fold(Vec2.Zero) { p,b -> p+b.velocity*b.mass }
        assertEquals(initialMomentum.x,momentum.x,1e-5); assertEquals(initialMomentum.y,momentum.y,1e-5)
        val maximum=approximate.indices.maxOf { (approximate[it].position-exact[it].position).magnitude() }
        println("BH 2s max position error=$maximum energy relative error=${abs(SimulationEngine.totalEnergy(approximate)/energy-1)}")
        assertTrue("Trajectory error: $maximum",maximum < 2.0)
        assertTrue(abs(SimulationEngine.totalEnergy(approximate)/energy-1) < .003)
    }
    @Test fun benchmarkFiveHundredBodyIntegration() {
        val bodies=scene(); val step=BarnesHutGravity.stepLimit(bodies)
        repeat(100) { NumericIntegrator.advance(bodies,1.0/60,step); NumericIntegrator.advance(bodies,1.0/60,step,approximateGravity=false) }
        fun measure(approximate: Boolean): Double {
            val start=System.nanoTime()
            repeat(60) { NumericIntegrator.advance(bodies,1.0/60,step,approximateGravity=approximate) }
            return (System.nanoTime()-start)/1e6/60
        }
        val exact=List(5) { measure(false) }.sorted()[2]; val tree=List(5) { measure(true) }.sorted()[2]
        println("500 bodies: exact RK4=$exact ms Barnes-Hut Verlet=$tree ms speedup=${exact/tree}")
    }
}
