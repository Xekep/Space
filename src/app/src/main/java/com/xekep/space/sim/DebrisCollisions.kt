package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class SandboxCollisionMode { Merge, Debris }

private class SweepBounds {
    var index=0; var left=0.0; var right=0.0; var top=0.0; var bottom=0.0
}
private val sweepWorkspace=ThreadLocal<ArrayList<SweepBounds>>()

/** Sweep-and-prune broad phase. Swept bounds preserve contacts between fast moving bodies.
 * Reuse boxes and sweep along the wider axis, avoiding quadratic work in a vertical cluster. */
internal fun collisionPairs(bodies: List<CelestialBody>,previous: List<CelestialBody>,
    before: Map<Long,CelestialBody>? = null,margin: Double = 1.0): List<Pair<Int,Int>> {
    val aligned=before == null && bodies.size == previous.size && bodies.indices.all { bodies[it].id == previous[it].id }
    val lookup=before ?: if (aligned) null else previous.associateBy { it.id }
    val bounds=sweepWorkspace.get() ?: ArrayList<SweepBounds>().also(sweepWorkspace::set)
    while (bounds.size < bodies.size) bounds+=SweepBounds()
    while (bounds.size > bodies.size) bounds.removeAt(bounds.lastIndex)
    var minX=Double.POSITIVE_INFINITY; var maxX=Double.NEGATIVE_INFINITY
    var minY=Double.POSITIVE_INFINITY; var maxY=Double.NEGATIVE_INFINITY
    bodies.forEachIndexed { i,body ->
        val start=if (aligned) previous[i].position else lookup?.get(body.id)?.position ?: body.position
        val radius=body.radius*(if (body.isVehicle) 1.4 else margin)
        bounds[i].apply {
            index=i; left=min(start.x,body.position.x)-radius; right=max(start.x,body.position.x)+radius
            top=min(start.y,body.position.y)-radius; bottom=max(start.y,body.position.y)+radius
            minX=min(minX,left); maxX=max(maxX,right); minY=min(minY,top); maxY=max(maxY,bottom)
        }
    }
    val horizontal=maxX-minX >= maxY-minY
    bounds.sortWith(if (horizontal) compareBy { it.left } else compareBy { it.top })
    val pairs=ArrayList<Pair<Int,Int>>()
    for (i in bounds.indices) {
        val first=bounds[i]
        for (j in i+1 until bounds.size) {
            val second=bounds[j]
            if (if (horizontal) second.left > first.right else second.top > first.bottom) break
            if (if (horizontal) first.bottom < second.top || second.bottom < first.top
                else first.right < second.left || second.right < first.left) continue
            pairs+=min(first.index,second.index) to max(first.index,second.index)
        }
    }
    return pairs
}

internal class EjectaBudget(var remaining: Int)

/** Absorb both bodies into one remnant, ejecting a bounded fraction of matter on energetic
 * impacts. Symmetric ejecta carry their share of mass and momentum; the remaining kinetic
 * energy is dissipated. Debris is absorbed without spawning another generation. */
internal fun debrisCollisions(bodies: List<CelestialBody>,previous: List<CelestialBody>,seconds: Double,
    budget: EjectaBudget = EjectaBudget(Int.MAX_VALUE),newId: () -> Long): StepResult {
    val aligned=bodies.size == previous.size && bodies.indices.all { bodies[it].id == previous[it].id }
    val before=if (aligned) null else previous.associateBy { it.id }
    fun prior(index: Int,body: CelestialBody) = if (aligned) previous[index] else before?.get(body.id) ?: body
    val pairs=collisionPairs(bodies,previous,before).mapNotNull { (i,j) ->
        val a=bodies[i]; val b=bodies[j]
        if (a.isVehicle || b.isVehicle || a.kind == BodyKind.BlackHole || b.kind == BodyKind.BlackHole) null
        else bodyContact(prior(i,a),a,prior(j,b),b)?.let { Triple(i,j,it) }
    }.sortedBy { it.third.fraction }
    if (pairs.isEmpty()) return StepResult(bodies,emptyList())
    val result=bodies.toMutableList(); val events=ArrayList<CollisionEvent>()
    val touched=BooleanArray(bodies.size); val removed=BooleanArray(bodies.size)
    var activeCount=bodies.size
    for ((i,j,contact) in pairs) {
        if (touched[i] || touched[j]) continue
        val a=result[i]; val b=result[j]
        touched[i]=true; touched[j]=true; removed[j]=true; activeCount--
        val p1=prior(i,a).position+(a.position-prior(i,a).position)*contact.fraction
        val p2=prior(j,b).position+(b.position-prior(j,b).position)*contact.fraction
        val mass=a.mass+b.mass
        val velocity=(a.velocity*a.mass+b.velocity*b.mass)/mass
        val position=(p1*a.mass+p2*b.mass)/mass+velocity*seconds*(1-contact.fraction)
        val relative=b.velocity-a.velocity
        val speed=relative.magnitude()
        val normal=(p2-p1).normalized().takeUnless { it == Vec2.Zero }
            ?: relative.normalized().takeUnless { it == Vec2.Zero } ?: Vec2(1.0,0.0)
        val escape=sqrt(800.0*mass/(a.radius+b.radius+forceSoftening(a,b)))
        val energy=.5*(a.mass*b.mass/mass)*speed*speed
        val perImpact=if (bodies.size >= BARNES_HUT_THRESHOLD) 2 else if (bodies.size >= 80) 4 else 6
        val count=if (a.isDebris || b.isDebris || speed <= max(12.0,escape*.3)) 0
            else minOf(perImpact,MAX_SANDBOX_BODIES-activeCount,budget.remaining)/2*2
        val loss=if (count == 0) 0.0 else min(a.mass,b.mass)*(speed/(escape+1)*.04).coerceIn(.015,.12)
        val volume=a.radius.toDouble().pow(3)+b.radius.toDouble().pow(3)
        val remnantRadius=cbrt(volume*(1-loss/mass)).toFloat()
        if (count > 0) {
            budget.remaining-=count; activeCount+=count
            val radius=cbrt(volume*loss/mass/count).toFloat()
            val spread=min(sqrt(2*energy*.25/loss),speed*.5)
            val tangent=normal.perpendicular()
            repeat(count/2) { pair ->
                val angle=(pair-(count/2-1)*.5)*.45
                val direction=tangent*cos(angle)+normal*sin(angle)
                for (sign in listOf(-1.0,1.0)) result+=CelestialBody(newId(),
                    position+direction*(sign*(remnantRadius+radius*2.5)),velocity+direction*(sign*spread),loss/count,radius,
                    Color((a.color.red+b.color.red)*.5f,(a.color.green+b.color.green)*.5f,(a.color.blue+b.color.blue)*.5f),
                    physicalScale=a.physicalScale || b.physicalScale,isDebris=true,galaxyParticle=a.galaxyParticle && b.galaxyParticle)
            }
        }
        val dominant=if (a.mass >= b.mass) a else b
        val kind=when {
            a.kind == BodyKind.Core || b.kind == BodyKind.Core -> BodyKind.Core
            a.kind == BodyKind.Star || b.kind == BodyKind.Star -> BodyKind.Star
            else -> dominant.kind
        }
        result[i]=dominant.copy(mass=mass-loss,position=position,velocity=velocity,radius=remnantRadius,
            kind=kind,solar=null,physicalScale=a.physicalScale || b.physicalScale,
            isDebris=a.isDebris && b.isDebris,trail=listOf(position))
        if (count > 0 || speed > max(4.0,escape*.02))
            events+=CollisionEvent(a.kind,b.kind,position,velocity=velocity,seed=(a.id xor b.id).toInt(),debrisImpact=true)
    }
    return StepResult(result.filterIndexed { index,_ -> index >= removed.size || !removed[index] },events)
}
