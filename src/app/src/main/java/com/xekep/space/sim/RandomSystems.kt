package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/** Ten separated stellar systems, each with 1 star, 8 planets, 8 moons and 33 asteroids. */
object RandomSystems {
    const val BODY_COUNT=500
    fun create(random: Random = Random.Default,newId: () -> Long): List<CelestialBody> {
        val result=ArrayList<CelestialBody>(BODY_COUNT)
        repeat(10) { system ->
            val center=Vec2((system%5-2)*7500.0+random.nextDouble(-400.0,400.0),
                (system/5-.5)*7500.0+random.nextDouble(-400.0,400.0))
            val starMass=random.nextDouble(8000.0,14000.0)
            val star=CelestialBody(newId(),center,Vec2.Zero,starMass,SimulationEngine.radiusForMass(starMass),
                Color(1f,random.nextDouble(.7,1.0).toFloat(),random.nextDouble(.35,.7).toFloat()),BodyKind.Star)
            val group=ArrayList<CelestialBody>(50); group+=star
            val scale=random.nextDouble(.9,1.1)
            listOf(170.0,260.0,400.0,620.0,950.0,1350.0,1850.0,2400.0).forEachIndexed { index,axis ->
                val angle=random.nextDouble(2*PI)
                val point=center+Vec2(cos(angle),sin(angle))*(axis*scale)
                val mass=random.nextDouble(120.0,400.0)
                val planet=CelestialBody(newId(),point,SimulationEngine.orbitVelocity(star,point,mass),mass,
                    SimulationEngine.radiusForMass(mass),Color(random.nextFloat()*.5f+.4f,random.nextFloat()*.5f+.4f,random.nextFloat()*.5f+.4f))
                group+=planet
                if (index >= 4) repeat(2) { moonIndex ->
                    val moonAngle=random.nextDouble(2*PI)
                    val radius=if (moonIndex == 0) 25.0 else 43.0
                    val moonPoint=point+Vec2(cos(moonAngle),sin(moonAngle))*radius
                    val moonMass=random.nextDouble(.1,.5)
                    group+=CelestialBody(newId(),moonPoint,SimulationEngine.orbitVelocity(planet,moonPoint,moonMass),moonMass,
                        random.nextDouble(.6,1.2).toFloat(),Color(0xFFB8C5D9))
                }
            }
            repeat(33) { asteroidIndex ->
                val angle=asteroidIndex*2*PI/33+random.nextDouble(-.025,.025)
                val point=center+Vec2(cos(angle),sin(angle))*random.nextDouble(2700.0,3200.0)
                val mass=random.nextDouble(.02,.15)
                group+=CelestialBody(newId(),point,SimulationEngine.orbitVelocity(star,point,mass),mass,
                    random.nextDouble(.35,.75).toFloat(),Color(0xFF9FAEC2))
            }
            val total=group.sumOf { it.mass }
            val drift=group.fold(Vec2.Zero) { v,b -> v+b.velocity*b.mass }/total
            val bulk=Vec2(random.nextDouble(-2.0,2.0),random.nextDouble(-2.0,2.0))
            result+=group.map { it.copy(velocity=it.velocity-drift+bulk) }
        }
        return result
    }
}
