package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/** Ten separated planetary systems. Random phases and colors vary the scene, while radial
 * bands stay disjoint. No incoming streams or close binaries are injected at creation. */
object RandomSystems {
    const val BODY_COUNT=500
    private val planetColors=listOf(Color(0xFF88BADF),Color(0xFFC18B77),Color(0xFFA0C7AD),Color(0xFFCDBA83),Color(0xFFB49CDC))

    fun create(random: Random = Random.Default,newId: () -> Long): List<CelestialBody> {
        val result=ArrayList<CelestialBody>(BODY_COUNT)
        val stars=List(10) { index ->
            val angle=index*2*PI/10+random.nextDouble(-.035,.035)
            val center=Vec2(cos(angle),sin(angle))*random.nextDouble(37000.0,40000.0)
            val mass=random.nextDouble(10000.0,17000.0)
            CelestialBody(newId(),center,Vec2.Zero,mass,SimulationEngine.radiusForMass(mass),
                Color(1f,random.nextDouble(.65,1.0).toFloat(),random.nextDouble(.35,.85).toFloat()),BodyKind.Star)
        }
        // A slow common rotation supports the stellar cluster instead of aiming stars inward.
        val angularRate=sqrt(stars.indices.sumOf { i ->
            val star=stars[i]; val radial=star.position.normalized()
            val acceleration=stars.filter { it.id != star.id }.fold(Vec2.Zero) { acc,other ->
                val delta=other.position-star.position; val square=delta.x*delta.x+delta.y*delta.y+18*18
                acc+delta*(400*other.mass/(square*sqrt(square)))
            }
            -(acceleration.x*radial.x+acceleration.y*radial.y)/star.position.magnitude()
        }/stars.size)
        for (base in stars) {
            val star=base.copy(velocity=base.position.perpendicular()*angularRate)
            result+=star
            val reverse=random.nextBoolean()
            val scale=random.nextDouble(.96,1.04)
            fun orbit(parent: CelestialBody,axis: Double,mass: Double,radius: Float,color: Color,phase: Double): CelestialBody {
                val position=parent.position+Vec2(cos(phase),sin(phase))*axis
                var velocity=SimulationEngine.orbitVelocity(parent,position,mass)
                if (reverse) velocity=parent.velocity-(velocity-parent.velocity)
                return CelestialBody(newId(),position,velocity,mass,radius,color)
            }
            repeat(8) { index ->
                val axis=130*1.67.pow(index)*scale
                val mass=if (index < 4) random.nextDouble(.7,2.0) else random.nextDouble(10.0,14.0)
                val planet=orbit(star,axis,mass,SimulationEngine.radiusForMass(mass),
                    planetColors[random.nextInt(planetColors.size)],random.nextDouble(2*PI))
                result+=planet
                if (index >= 5) {
                    val phase=random.nextDouble(2*PI)
                    repeat(if (index == 5) 2 else 3) { moon ->
                        val moonMass=mass*random.nextDouble(.00001,.00003)
                        val radius=(planet.radius*cbrt(moonMass/mass)).toFloat()
                        result+=orbit(planet,listOf(14.0,24.0,36.0)[moon],moonMass,radius,
                            Color(0xFFBDCDDA),phase+moon*2*PI/(if (index == 5) 2 else 3))
                    }
                }
            }
            // Cold outer belt: small masses, one circulation direction, separated phases.
            val phase=random.nextDouble(2*PI)
            repeat(33) { index ->
                val axis=(6100+index*20)*scale
                result+=orbit(star,axis,random.nextDouble(.0001,.0005),random.nextDouble(.35,.65).toFloat(),
                    Color(0xFFAD9986),phase+index*2*PI/33+random.nextDouble(-.015,.015))
            }
        }
        val total=result.sumOf { it.mass }
        val common=result.fold(Vec2.Zero) { momentum,body -> momentum+body.velocity*body.mass }/total
        return result.map { it.copy(velocity=it.velocity-common) }
    }
}
