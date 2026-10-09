package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/** Resolved hierarchical systems in a rotating disk, rather than coarse stellar tracers.
 * Local orbits inherit their parent's velocity. Hill margins and separated stellar centres
 * keep creation gentle; the subsequent evolution uses ordinary N-body gravity.
 */
object SystemGalaxy {
    const val BODY_COUNT=1000
    const val SYSTEM_COUNT=111
    private const val INNER=26000.0
    private const val BAND=1800.0
    private const val BANDS=25
    private const val SEPARATION=5500.0
    private data class Centre(val point: Vec2,val band: Int,val tint: Color)
    private val stars=listOf(Color(0xFFFFD99C),Color(0xFFFFBA8C),Color(0xFFB7DCFF),Color(0xFFDFCDFF))
    private val planets=listOf(Color(0xFF82C9DE),Color(0xFFC6A7EF),Color(0xFFE5AA78),Color(0xFF91CBA7),Color(0xFFB9C8D8))

    fun create(random: Random = Random.Default,newId: () -> Long): List<CelestialBody> {
        val phase=random.nextDouble(2*PI); val arms=random.nextInt(2,5)
        val winding=random.nextDouble(2.0,4.0)*(if (random.nextBoolean()) 1 else -1)
        val spin=if (random.nextBoolean()) 1.0 else -1.0
        fun spiral(radius: Double,arm: Int)=phase+arm*2*PI/arms+winding*ln(radius/INNER)
        fun gaussian()=sqrt(-2*ln(random.nextDouble().coerceAtLeast(1e-12)))*cos(random.nextDouble(2*PI))
        val knots=List(6) { index ->
            val radius=random.nextDouble(38000.0,64000.0)
            radius to spiral(radius,index%arms)
        }
        val centres=ArrayList<Centre>(SYSTEM_COUNT)
        fun clear(point: Vec2)=centres.none { (point-it.point).magnitude() < SEPARATION }
        repeat(SYSTEM_COUNT) { index ->
            var band=0; var point=Vec2.Zero; var attempts=0
            do {
                val knot=knots[index%knots.size]
                val radius=when {
                    index < 20 -> random.nextDouble(INNER,37000.0)
                    index >= 80 && attempts < 80 -> knot.first+gaussian()*5000.0
                    else -> random.nextDouble(INNER,INNER+BAND*(BANDS-1))
                }
                band=((radius-INNER)/BAND).roundToInt().coerceIn(0,BANDS-1)
                val ring=INNER+band*BAND
                val angle=when {
                    index >= 80 && attempts < 80 -> knot.second+gaussian()*.15
                    index >= 20 && attempts < 80 -> spiral(ring,random.nextInt(arms))+gaussian()*.16
                    else -> random.nextDouble(2*PI)
                }
                point=Vec2(cos(angle),sin(angle))*ring
                attempts++
                if (attempts >= 200) {
                    // Bounded fallback: at this occupancy a dispersed grid has ample space.
                    val slot=(0 until BANDS*96).first { candidate ->
                        val r=INNER+(candidate/96)*BAND
                        val a=phase+(candidate%96)*2*PI/96
                        clear(Vec2(cos(a),sin(a))*r)
                    }
                    band=slot/96
                    val a=phase+(slot%96)*2*PI/96
                    point=Vec2(cos(a),sin(a))*(INNER+band*BAND)
                }
            } while (!clear(point))
            centres+=Centre(point,band,stars[(index/9+random.nextInt(stars.size))%stars.size])
        }
        val hole=CelestialBody(newId(),Vec2.Zero,Vec2.Zero,random.nextDouble(1600000.0,2000000.0),13f,
            Color(0xFFBCACFF),BodyKind.BlackHole,physicalScale=true)
        val families=centres.map { centre ->
            val id=newId()
            val category=random.nextDouble()
            val starMass=when { category < .30 -> random.nextDouble(4000.0,6000.0); category > .85 -> random.nextDouble(12000.0,18000.0); else -> random.nextDouble(6500.0,11000.0) }
            val starRadius=when { category < .30 -> random.nextDouble(5.0,12.0); category > .85 -> random.nextDouble(42.0,68.0); else -> random.nextDouble(16.0,30.0) }
            val tint=when { category < .30 -> Color(0xFFFFA78F); category > .85 -> Color(0xFFFFD18E); else -> centre.tint }
            val star=CelestialBody(id,Vec2.Zero,Vec2.Zero,starMass,starRadius.toFloat(),tint,
                BodyKind.Star,physicalScale=true,galaxySystemId=id)
            val local=ArrayList<CelestialBody>(9); local+=star
            val orbitSpin=if (random.nextBoolean()) 1.0 else -1.0
            for (index in 0..2) {
                val radius=listOf(180.0,480.0,1100.0)[index]*random.nextDouble(.92,1.08)
                val angle=random.nextDouble(2*PI)
                val point=Vec2(cos(angle),sin(angle))*radius
                val mass=when (index) { 0 -> random.nextDouble(4.0,8.0); 1 -> random.nextDouble(35.0,50.0); else -> random.nextDouble(65.0,90.0) }
                val planet=CelestialBody(newId(),point,SimulationEngine.orbitVelocity(star,point,mass)*orbitSpin,
                    mass,(1.5+cbrt(mass)*.6).toFloat(),planets[random.nextInt(planets.size)],BodyKind.Ambient,
                    physicalScale=true,galaxySystemId=id,orbitParentId=id)
                val group=ArrayList<CelestialBody>(); group+=planet
                val hill=radius*cbrt(mass/(3*starMass))
                val fractions=when (index) { 0 -> emptyList(); 1 -> listOf(.18,.34); else -> listOf(.12,.21,.34) }
                val moonSpin=if (random.nextDouble() < .15) -orbitSpin else orbitSpin
                for (fraction in fractions) {
                    val moonRadius=hill*fraction
                    val moonAngle=random.nextDouble(2*PI)
                    val moonPoint=point+Vec2(cos(moonAngle),sin(moonAngle))*moonRadius
                    val moonMass=mass*random.nextDouble(.00005,.00015)
                    val orbit=SimulationEngine.orbitVelocity(planet,moonPoint,moonMass)-planet.velocity
                    group+=CelestialBody(newId(),moonPoint,planet.velocity+orbit*moonSpin,moonMass,
                        random.nextDouble(.35,1.0).toFloat(),Color(0xFFD6DAE8),BodyKind.Ambient,
                        physicalScale=true,galaxySystemId=id,orbitParentId=planet.id)
                }
                // Planet and moons orbit their common centre, which follows the star.
                local+=recenter(group,point,planet.velocity)
            }
            recenter(local,centre.point,Vec2.Zero)
        }
        val familyMass=families.map { group -> group.sumOf { it.mass } }
        val speeds=centres.map { it.band }.distinct().associateWith { band ->
            val radius=INNER+band*BAND; var inward=0.0
            repeat(16) { sample ->
                val radial=Vec2(cos(sample*2*PI/16),sin(sample*2*PI/16))
                val point=radial*radius
                inward+=SimulationEngine.gravitationalConstant*hole.mass/(radius*radius)
                centres.forEachIndexed { index,centre ->
                    val delta=centre.point-point
                    // Average the large scale field; no self-force singularity at a host centre.
                    val square=delta.x*delta.x+delta.y*delta.y+700.0*700.0
                    inward-=(delta.x*radial.x+delta.y*radial.y)*SimulationEngine.gravitationalConstant*familyMass[index]/(square*sqrt(square))
                }
            }
            sqrt((inward/16*radius).coerceAtLeast(0.0))
        }
        val result=ArrayList<CelestialBody>(BODY_COUNT); result+=hole
        families.forEachIndexed { index,group ->
            val velocity=centres[index].point.perpendicular().normalized()*speeds.getValue(centres[index].band)*spin
            result+=group.map { it.copy(velocity=it.velocity+velocity) }
        }
        return recenter(result,Vec2.Zero,Vec2.Zero)
    }

    private fun recenter(bodies: List<CelestialBody>,point: Vec2,velocity: Vec2): List<CelestialBody> {
        val mass=bodies.sumOf { it.mass }
        val centre=bodies.fold(Vec2.Zero) { sum,body -> sum+body.position*body.mass }/mass
        val common=bodies.fold(Vec2.Zero) { sum,body -> sum+body.velocity*body.mass }/mass
        return bodies.map { body ->
            val position=body.position-centre+point
            body.copy(position=position,velocity=body.velocity-common+velocity,trail=listOf(position))
        }
    }
}
