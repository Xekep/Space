package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/** A cold, self-gravitating stellar disk. Morphology varies, not just the palette.
 * Narrow radial bands share a circular speed; local spacing prevents creation-time contacts.
 * The compact nucleus dominates the potential so a 500-particle toy disk starts gently.
 * This is a gameplay galaxy, not a dark-matter/three-dimensional galaxy model. */
object RandomSystems {
    const val BODY_COUNT=500
    private const val INNER=680.0
    private const val BAND=78.0
    private const val BANDS=59
    private val warm=listOf(Color(0xFFFFD9A1),Color(0xFFFFBE8C),Color(0xFFFFE9C8))
    private val blue=listOf(Color(0xFF92C9FF),Color(0xFFBAE0FF),Color(0xFFD6D7FF))
    private val red=listOf(Color(0xFFDF9BA9),Color(0xFFCDB1D9),Color(0xFFCEBDA3))
    private data class Knot(val radius: Double,val angle: Double,val width: Double,val palette: List<Color>)

    fun create(random: Random = Random.Default,newId: () -> Long): List<CelestialBody> {
        val phase=random.nextDouble(2*PI)
        val arms=random.nextInt(2,6)
        val winding=random.nextDouble(1.7,3.5)*(if (random.nextBoolean()) 1 else -1)
        val morphology=random.nextInt(3) // spiral, barred spiral, broken ring
        val direction=if (random.nextBoolean()) 1.0 else -1.0
        val scale=random.nextDouble(.93,1.07)
        fun armAngle(radius: Double,arm: Int)=phase+arm*2*PI/arms+winding*ln(radius/INNER)
        val knots=List(random.nextInt(5,9)) { i ->
            val radius=random.nextDouble(1800.0,4400.0)
            Knot(radius,armAngle(radius,i%arms)+random.nextDouble(-.18,.18),random.nextDouble(150.0,320.0),
                if (i%3 == 0) warm else if (i%3 == 1) blue else red)
        }
        val result=ArrayList<CelestialBody>(BODY_COUNT)
        result+=CelestialBody(newId(),Vec2.Zero,Vec2.Zero,random.nextDouble(480000.0,550000.0),13f,
            Color(0xFFBCACFF),BodyKind.BlackHole)
        val spacingSquared=160.0*scale*160.0*scale
        fun clear(point: Vec2)=result.none { body ->
            val dx=body.position.x-point.x; val dy=body.position.y-point.y
            dx*dx+dy*dy < spacingSquared
        }
        val bands=ArrayList<Int>(BODY_COUNT)
        bands+=-1
        fun gaussian()=sqrt(-2*ln(random.nextDouble().coerceAtLeast(1e-12)))*cos(random.nextDouble(2*PI))
        repeat(BODY_COUNT-1) { index ->
            // A bulge, disk/arms, and irregular associations; no ten cloned solar systems.
            val population=if (index < 90) 0 else if (index < 350) 1 else 2
            val knot=knots[(index-350).coerceAtLeast(0)%knots.size]
            var band: Int; var point: Vec2; var attempts=0
            do {
                val radius=when(population) {
                    0 -> random.nextDouble(INNER,1550.0)
                    1 -> INNER+(-ln(random.nextDouble().coerceAtLeast(1e-12))*1900).coerceIn(850.0, BAND*(BANDS-1))
                    else -> knot.radius+gaussian()*knot.width
                }
                band=((radius-INNER)/BAND).roundToInt().coerceIn(0,BANDS-1)
                val ring=INNER+band*BAND
                val angle=when {
                    population == 0 -> if (morphology == 1 && random.nextDouble() < .65)
                        phase+(if (random.nextBoolean()) PI else 0.0)+gaussian()*.30 else random.nextDouble(2*PI)
                    population == 2 && attempts < 80 -> knot.angle+gaussian()*knot.width/ring
                    population == 1 && morphology == 2 && random.nextDouble() < .65 ->
                        phase+random.nextInt(arms)*2*PI/arms+gaussian()*.40
                    population == 1 && random.nextDouble() < .70 && attempts < 80 ->
                        armAngle(ring,random.nextInt(arms))+gaussian()*.15
                    else -> random.nextDouble(2*PI)
                }
                // A ring morphology changes radial density too, rather than recoloring a spiral.
                if (population == 1 && morphology == 2 && random.nextDouble() < .6 && attempts < 80)
                    band=((random.nextDouble(2600.0,3700.0)-INNER)/BAND).roundToInt()
                point=Vec2(cos(angle),sin(angle))*(INNER+band*BAND)*scale
                attempts++
                // Keep the fallback bounded and dispersed after a crowded association fills.
                if (attempts == 240) {
                    // Search a dispersed grid after the association fills.
                    for (slot in 0 until BANDS*64) {
                        band=slot/64
                        val fallback=phase+slot%64*2*PI/64
                        point=Vec2(cos(fallback),sin(fallback))*(INNER+band*BAND)*scale
                        if (clear(point)) break
                    }
                }
            } while(!clear(point))
            val fraction=random.nextDouble()
            val mass=if (fraction > .96) random.nextDouble(4.5,6.5) else random.nextDouble(1.2,3.2)
            val radius=(4.3+2.9*cbrt(mass/6.5)).toFloat()
            val isStar=index%7 != 0
            val palette=when(population) { 0 -> warm; 2 -> knot.palette; else -> if (band > 40) red else blue }
            result+=CelestialBody(newId(),point,Vec2.Zero,mass,radius,palette[random.nextInt(palette.size)],
                if (isStar) BodyKind.Star else BodyKind.Ambient,galaxyParticle=true)
            bands+=band
        }
        // Average the actual softened radial field around each occupied band. All bodies on
        // that band receive the same angular rate, avoiding an immediate crossing of neighbors.
        val speeds=bands.filter { it >= 0 }.distinct().associateWith { band ->
            val radius=(INNER+band*BAND)*scale
            var inward=0.0
            repeat(16) { sample ->
                val radial=Vec2(cos(sample*2*PI/16),sin(sample*2*PI/16))
                val x=radial.x*radius; val y=radial.y*radius
                for (source in result) {
                    val dx=source.position.x-x; val dy=source.position.y-y
                    val soft=minOf(GALAXY_SOFTENING,source.gravitySoftening)
                    val square=dx*dx+dy*dy+soft*soft
                    inward-=(dx*radial.x+dy*radial.y)*SimulationEngine.gravitationalConstant*source.gravityMass/(square*sqrt(square))
                }
            }
            sqrt((inward/16*radius).coerceAtLeast(0.0))
        }
        val moving=result.mapIndexed { index,body ->
            if (index == 0) body else body.copy(velocity=body.position.perpendicular().normalized()*speeds.getValue(bands[index])*direction)
        }
        val total=moving.sumOf { it.mass }
        val center=moving.fold(Vec2.Zero) { sum,body -> sum+body.position*body.mass }/total
        val common=moving.fold(Vec2.Zero) { sum,body -> sum+body.velocity*body.mass }/total
        return moving.map { body ->
            val point=body.position-center
            body.copy(position=point,velocity=body.velocity-common,trail=listOf(point))
        }
    }
}
