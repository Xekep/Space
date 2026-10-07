package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class SandboxCollisionMode { Merge, Debris }

/** Sweep-and-prune broad phase, including the whole travelled segment of fast bodies. */
internal fun collisionPairs(bodies: List<CelestialBody>,previous: List<CelestialBody>): List<Pair<Int,Int>> {
    val before=previous.associateBy { it.id }
    data class Bounds(val index: Int,val left: Double,val right: Double,val top: Double,val bottom: Double)
    val bounds=bodies.mapIndexed { i,b ->
        val start=before[b.id]?.position ?: b.position
        val radius=b.radius*1.4
        Bounds(i,min(start.x,b.position.x)-radius,max(start.x,b.position.x)+radius,
            min(start.y,b.position.y)-radius,max(start.y,b.position.y)+radius)
    }.sortedBy { it.left }
    val pairs=ArrayList<Pair<Int,Int>>()
    for (i in bounds.indices) {
        val first=bounds[i]
        for (j in i+1 until bounds.size) {
            val second=bounds[j]
            if (second.left > first.right) break
            if (first.bottom < second.top || second.bottom < first.top) continue
            pairs+=min(first.index,second.index) to max(first.index,second.index)
        }
    }
    return pairs
}

/** Inelastic contact plus a small, energy-limited ejecta budget. Ejecta are real gravitational
 * bodies. Removed mass and momentum are transferred to them, including at the scene limit. */
internal fun debrisCollisions(bodies: List<CelestialBody>,previous: List<CelestialBody>,seconds: Double,
    newId: () -> Long): StepResult {
    val before=previous.associateBy { it.id }
    val result=bodies.toMutableList(); val events=ArrayList<CollisionEvent>()
    val touched=BooleanArray(bodies.size)
    val removed=BooleanArray(bodies.size)
    val pairs=collisionPairs(bodies,previous).mapNotNull { (i,j) ->
        val a=bodies[i]; val b=bodies[j]
        if (a.isVehicle || b.isVehicle || a.kind == BodyKind.BlackHole || b.kind == BodyKind.BlackHole) null
        else bodyContact(before[a.id] ?: a,a,before[b.id] ?: b,b)?.let { Triple(i,j,it) }
    }.sortedBy { it.third.fraction }
    for ((i,j,contact) in pairs) {
        if (touched[i] || touched[j]) continue
        var a=result[i]; var b=result[j]
        val aStart=before[a.id]?.position ?: a.position; val bStart=before[b.id]?.position ?: b.position
        var p1=aStart+(a.position-aStart)*contact.fraction
        var p2=bStart+(b.position-bStart)*contact.fraction
        var normal=(p2-p1).normalized()
        if (normal == Vec2.Zero) normal=(a.velocity-b.velocity).normalized().takeUnless { it == Vec2.Zero } ?: Vec2(1.0,0.0)
        val mass=a.mass+b.mass
        val gap=(a.radius+b.radius-(p2-p1).magnitude()).coerceAtLeast(0.0)+max(1e-8,min(a.radius,b.radius)*1e-4)
        p1-=normal*(gap*b.mass/mass); p2+=normal*(gap*a.mass/mass)
        val relative=b.velocity-a.velocity
        val approach=-(relative.x*normal.x+relative.y*normal.y)
        if (approach <= 1e-8) {
            result[i]=a.copy(position=p1+a.velocity*seconds*(1-contact.fraction))
            result[j]=b.copy(position=p2+b.velocity*seconds*(1-contact.fraction))
            continue
        }
        touched[i]=true; touched[j]=true
        val restitution=.55
        val impulse=(1+restitution)*approach/(1/a.mass+1/b.mass)
        val v1=a.velocity-normal*(impulse/a.mass); val v2=b.velocity+normal*(impulse/b.mass)
        val impact=p1+normal*a.radius.toDouble()
        val reducedMass=a.mass*b.mass/mass
        val dissipated=.5*reducedMass*approach*approach*(1-restitution*restitution)
        val escape=sqrt(800.0*mass/(a.radius+b.radius+forceSoftening(a,b)))
        val stellar=(a.kind == BodyKind.Star || a.kind == BodyKind.Core) && (b.kind == BodyKind.Star || b.kind == BodyKind.Core)
        val count=if (a.isDebris || b.isDebris) 0 else min(6,1000-result.size)/2*2
        if (count >= 2 && approach > max(12.0,escape*.3)) {
            val loss=min(a.mass,b.mass)*(approach/(escape+1)*.04).coerceIn(.015,.12)
            val loss1=loss*b.mass/mass; val loss2=loss*a.mass/mass
            val fragmentMass=loss/count
            val volume=a.radius.toDouble().pow(3)*loss1/a.mass+b.radius.toDouble().pow(3)*loss2/b.mass
            val radius=cbrt(volume/count).coerceAtLeast(1e-7).toFloat()
            val base=(v1*loss1+v2*loss2)/loss
            val spread=min(sqrt(2*dissipated*.12/loss),approach*.35)
            val tangent=normal.perpendicular()
            val remnantRadius=cbrt(a.radius.toDouble().pow(3)+b.radius.toDouble().pow(3)-volume)
            val debrisCenter=if (stellar) (p1*a.mass+p2*b.mass)/mass else impact
            val offset=if (stellar) remnantRadius+radius*2.2 else max(sqrt(2*max(a.radius,b.radius)*radius+radius*radius)*1.2,radius*2.2)
            repeat(count/2) { pair ->
                val angle=(pair-(count/2-1)*.5)*.3
                val direction=tangent*cos(angle)+normal*sin(angle)
                for (sign in listOf(-1.0,1.0)) result+=CelestialBody(newId(),
                    debrisCenter+tangent*(sign*(offset+pair*radius*2.4)),base+direction*(sign*spread),fragmentMass,radius,
                    Color((a.color.red+b.color.red)*.5f,(a.color.green+b.color.green)*.5f,(a.color.blue+b.color.blue)*.5f),
                    physicalScale=a.physicalScale || b.physicalScale,isDebris=true)
            }
            a=a.copy(mass=a.mass-loss1,radius=(a.radius*cbrt(1-loss1/a.mass)).toFloat(),solar=null)
            b=b.copy(mass=b.mass-loss2,radius=(b.radius*cbrt(1-loss2/b.mass)).toFloat(),solar=null)
        }
        if (stellar) {
            val combinedMass=a.mass+b.mass
            val velocity=(v1*a.mass+v2*b.mass)/combinedMass
            val position=(p1*a.mass+p2*b.mass)/combinedMass+velocity*seconds*(1-contact.fraction)
            val dominant=if (a.mass >= b.mass) a else b
            result[i]=dominant.copy(id=a.id,mass=combinedMass,position=position,velocity=velocity,
                radius=cbrt(a.radius.toDouble().pow(3)+b.radius.toDouble().pow(3)).toFloat(),solar=null,
                physicalScale=a.physicalScale || b.physicalScale)
            removed[j]=true
        } else {
            result[i]=a.copy(position=p1+v1*seconds*(1-contact.fraction),velocity=v1)
            result[j]=b.copy(position=p2+v2*seconds*(1-contact.fraction),velocity=v2)
        }
        events+=CollisionEvent(a.kind,b.kind,impact,velocity=(v1+v2)*.1,seed=(a.id xor b.id).toInt(),debrisImpact=true)
    }
    return StepResult(result.filterIndexed { index,_ -> index >= removed.size || !removed[index] },events)
}
