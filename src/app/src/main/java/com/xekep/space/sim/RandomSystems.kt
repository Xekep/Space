package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/** A compact cluster: three binary systems, four single stars, varied orbits and crossing streams. */
object RandomSystems {
    const val BODY_COUNT=500
    private data class Source(val star: CelestialBody,val center: Vec2,val drift: Vec2,val mass: Double,val separation: Double)
    private val planetColors=listOf(Color(0xFF88BADF),Color(0xFFC18B77),Color(0xFFA0C7AD),Color(0xFFCDBA83),Color(0xFFB49CDC))

    fun create(random: Random = Random.Default,newId: () -> Long): List<CelestialBody> {
        val result=ArrayList<CelestialBody>(BODY_COUNT)
        val centers=ArrayList<Vec2>(7)
        val sources=ArrayList<Source>(10)
        repeat(7) { group ->
            var center=Vec2.Zero
            repeat(40) placement@{
                if (center != Vec2.Zero) return@placement
                val angle=group*2*PI/7+random.nextDouble(-.3,.3)
                val point=Vec2(cos(angle),sin(angle))*random.nextDouble(3800.0,7200.0)
                if (centers.all { (it-point).magnitude() > 2600 }) center=point
            }
            if (center == Vec2.Zero) {
                val angle=group*2*PI/7
                center=Vec2(cos(angle),sin(angle))*9000.0
            }
            centers+=center
            val drift=center.perpendicular().normalized()*random.nextDouble(-12.0,12.0)+center.normalized()*random.nextDouble(-14.0,6.0)
            val firstMass=random.nextDouble(6000.0,22000.0)
            fun star(point: Vec2,velocity: Vec2,mass: Double,color: Color): CelestialBody =
                CelestialBody(newId(),point,velocity,mass,SimulationEngine.radiusForMass(mass),color,BodyKind.Star).also { result+=it }
            if (group < 3) {
                val secondMass=random.nextDouble(4500.0,16000.0)
                val total=firstMass+secondMass
                val separation=random.nextDouble(700.0,1150.0)
                val angle=random.nextDouble(2*PI)
                val direction=Vec2(cos(angle),sin(angle))
                val speed=sqrt(400*total*separation*separation/(separation*separation+18*18).pow(1.5))
                val first=star(center-direction*(separation*secondMass/total),drift-direction.perpendicular()*(speed*secondMass/total),firstMass,Color(0xFFFFCE83))
                val second=star(center+direction*(separation*firstMass/total),drift+direction.perpendicular()*(speed*firstMass/total),secondMass,Color(0xFF9BD5FF))
                sources+=Source(first,center,drift,total,separation)
                sources+=Source(second,center,drift,total,separation)
            } else {
                val body=star(center,drift,firstMass,Color(1f,random.nextDouble(.65,1.0).toFloat(),random.nextDouble(.35,.85).toFloat()))
                sources+=Source(body,center,drift,firstMass,0.0)
            }
        }
        fun available(body: CelestialBody)=result.all { (body.position-it.position).magnitude() > (body.radius+it.radius)*1.15 }
        fun place(make: () -> CelestialBody): CelestialBody {
            repeat(160) {
                val candidate=make()
                if (available(candidate)) return candidate.copy(id=newId()).also { result+=it }
            }
            // Extremely crowded random draws become distant wanderers rather than overlapping spawns.
            var candidate=make()
            var offset=0.0
            while (!available(candidate)) { offset+=180.0; candidate=candidate.copy(position=candidate.position+Vec2(offset,offset*.37)) }
            return candidate.copy(id=newId()).also { result+=it }
        }
        fun orbit(center: Vec2,bulk: Vec2,mass: Double,axis: Double,e: Double,retrograde: Boolean,satelliteMass: Double,
            radius: Float,color: Color): CelestialBody {
            val anomaly=random.nextDouble(2*PI)
            val orientation=random.nextDouble(2*PI)
            val angle=anomaly+orientation
            val distance=axis*(1-e*e)/(1+e*cos(anomaly))
            val radial=Vec2(cos(angle),sin(angle))
            val soft=(distance*distance/(distance*distance+18*18)).pow(.75)
            val speed=sqrt(400*(mass+satelliteMass)/(axis*(1-e*e)))*soft*(if (retrograde) -1 else 1)
            val velocity=bulk+(radial*(e*sin(anomaly))+radial.perpendicular()*(1+e*cos(anomaly)))*speed
            return CelestialBody(0,center+radial*distance,velocity,satelliteMass,radius,color)
        }
        sources.forEach { source ->
            val scale=random.nextDouble(.75,1.2)
            val reverse=random.nextDouble() < .25
            repeat(8) { index ->
                val local=source.separation == 0.0 || index < 3
                val center=if (local) source.star.position else source.center
                val bulk=if (local) source.star.velocity else source.drift
                val mass=if (local) source.star.mass else source.mass
                val axis=if (source.separation > 0) {
                    if (local) listOf(100.0,165.0,240.0)[index]*(source.separation/900)
                    else source.separation*listOf(1.55,2.1,2.75,3.45,4.2)[index-3]
                } else listOf(140.0,230.0,380.0,600.0,900.0,1350.0,1900.0,2600.0)[index]*scale
                val eccentricity=random.nextDouble(.04,if (source.separation > 0 && local) .22 else .48)
                val planetMass=random.nextDouble(45.0,650.0)
                val color=planetColors[random.nextInt(planetColors.size)]
                val planet=place { orbit(center,bulk,mass,axis,eccentricity,reverse,planetMass,SimulationEngine.radiusForMass(planetMass),color) }
                if (index >= 4) repeat(2) { moon ->
                    val moonMass=random.nextDouble(.05,.8)
                    val moonAxis=(if (moon == 0) 25.0 else 43.0)*random.nextDouble(.9,1.2)
                    val retrograde=random.nextDouble() < .3
                    place { orbit(planet.position,planet.velocity,planet.mass,moonAxis,.08,retrograde,moonMass,
                        random.nextDouble(.5,1.2).toFloat(),Color(0xFFBDCDDA)) }
                }
            }
            repeat(27) { index ->
                val mass=random.nextDouble(.02,.3)
                val binary=source.separation > 0
                val center=if (binary) source.center else source.star.position
                val bulk=if (binary) source.drift else source.star.velocity
                val centralMass=if (binary) source.mass else source.star.mass
                val axis=if (binary) source.separation*random.nextDouble(1.6,4.5) else
                    if (index < 18) random.nextDouble(1050.0,1600.0)*scale else random.nextDouble(2100.0,3700.0)*scale
                val e=if (index < 18) random.nextDouble(.1,.35) else random.nextDouble(.45,.7)
                place { orbit(center,bulk,centralMass,axis,e,reverse xor (index%7 == 0),mass,
                    random.nextDouble(.4,1.0).toFloat(),if (index < 18) Color(0xFFAD9986) else Color(0xFF83C5D1)) }
            }
        }
        repeat(60) { index ->
            val start=centers[index%centers.size]
            val end=centers[(index+1)%centers.size]
            val direction=(end-start).normalized()
            place {
                val point=start+(end-start)*random.nextDouble(.1,.9)+direction.perpendicular()*random.nextDouble(-550.0,550.0)
                CelestialBody(0,point,direction*random.nextDouble(35.0,115.0)+direction.perpendicular()*random.nextDouble(-30.0,30.0),
                    random.nextDouble(.02,.3),random.nextDouble(.4,1.1).toFloat(),Color(0xFF93CDE3))
            }
        }
        val total=result.sumOf { it.mass }
        val common=result.fold(Vec2.Zero) { momentum,body -> momentum+body.velocity*body.mass }/total
        return result.map { it.copy(velocity=it.velocity-common) }
    }
}
