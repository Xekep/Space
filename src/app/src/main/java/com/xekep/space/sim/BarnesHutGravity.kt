package com.xekep.space.sim

import kotlin.math.*

internal const val BARNES_HUT_THRESHOLD = 160

/** Pooled quadtree. Near neighbours remain exact; distant cells use their centres of mass.
 * Physical and playground masses are accumulated separately to preserve pair softening.
 * https://introcs.cs.princeton.edu/java/assignments/barnes-hut.html */
internal class BarnesHutGravity(private val theta: Double = .5) {
    private class Node {
        var x=0.0; var y=0.0; var width=0.0
        var mass=0.0; var mx=0.0; var my=0.0
        var physical=0.0; var px=0.0; var py=0.0
        var total=0.0; var cx=0.0; var cy=0.0; var nx=0.0; var ny=0.0
        var physicalX=0.0; var physicalY=0.0; var openingSquared=0.0; var branch=false
        var head=-1
        val children=IntArray(4) { -1 }
    }
    private val nodes=ArrayList<Node>()
    private var used=0
    private var links=IntArray(0)
    private var exactTargets=BooleanArray(0)
    private lateinit var state: DoubleArray
    private lateinit var masses: DoubleArray
    private lateinit var smoothing: DoubleArray
    var interactions=0; private set

    private fun node(x: Double,y: Double,width: Double): Int {
        val index=used++
        if (index == nodes.size) nodes.add(Node())
        nodes[index].apply {
            this.x=x; this.y=y; this.width=width
            mass=0.0; mx=0.0; my=0.0; physical=0.0; px=0.0; py=0.0; head=-1
            branch=false
            children.fill(-1)
        }
        return index
    }
    private fun quadrant(n: Node,i: Int) = (if (state[i*4] >= n.x) 1 else 0)+(if (state[i*4+1] >= n.y) 2 else 0)
    private fun child(n: Node,i: Int,depth: Int) {
        val q=quadrant(n,i)
        if (n.children[q] < 0) n.children[q]=node(n.x+(if (q and 1 == 0) -1 else 1)*n.width*.25,
            n.y+(if (q and 2 == 0) -1 else 1)*n.width*.25,n.width*.5)
        insert(n.children[q],i,depth+1)
    }
    private fun insert(index: Int,i: Int,depth: Int) {
        val n=nodes[index]
        val mass=masses[i]
        if (smoothing[i] < 1) { n.physical+=mass; n.px+=state[i*4]*mass; n.py+=state[i*4+1]*mass }
        else { n.mass+=mass; n.mx+=state[i*4]*mass; n.my+=state[i*4+1]*mass }
        if (n.branch) { child(n,i,depth); return }
        if (n.head < 0 || depth >= 48 || n.width < 1e-7) { links[i]=n.head; n.head=i; return }
        var old=n.head; n.head=-1; n.branch=true
        while (old >= 0) { val next=links[old]; child(n,old,depth); old=next }
        child(n,i,depth)
    }

    fun compute(values: DoubleArray,weights: DoubleArray,softening: DoubleArray,acceleration: DoubleArray,
        rates: DoubleArray? = null, travel: DoubleArray? = null, conserveMomentum: Boolean = true) {
        state=values; masses=weights; smoothing=softening
        val count=weights.size
        acceleration.fill(0.0); rates?.fill(0.0); travel?.fill(1.0/30)
        interactions=0; used=0
        if (count == 0) return
        if (links.size != count) { links=IntArray(count); exactTargets=BooleanArray(count) }
        links.fill(-1)
        // A few dominant stars must receive exact forces too: a small relative error on a
        // massive target otherwise produces a large centre-of-mass correction for tiny moons.
        // Cap this direct work at sixteen targets, retaining linear overhead as N grows.
        val dominant=(weights.maxOrNull() ?: 0.0)*.05
        var candidateCount=0
        for (i in weights.indices) if (weights[i] >= dominant) candidateCount++
        exactTargets.fill(false)
        if (candidateCount <= 16) for (i in weights.indices) exactTargets[i]=weights[i] >= dominant
        var minX=values[0]; var maxX=minX; var minY=values[1]; var maxY=minY
        for (i in 1 until count) {
            minX=min(minX,values[i*4]); maxX=max(maxX,values[i*4])
            minY=min(minY,values[i*4+1]); maxY=max(maxY,values[i*4+1])
        }
        node(minX+(maxX-minX)*.5,minY+(maxY-minY)*.5,max(maxX-minX,maxY-minY).coerceAtLeast(1e-6)*1.000001)
        for (i in 0 until count) insert(0,i,0)
        for (index in 0 until used) {
            val n=nodes[index]
            n.total=n.mass+n.physical
            n.cx=(n.mx+n.px)/n.total; n.cy=(n.my+n.py)/n.total
            if (n.mass > 0) { n.nx=n.mx/n.mass; n.ny=n.my/n.mass }
            if (n.physical > 0) { n.physicalX=n.px/n.physical; n.physicalY=n.py/n.physical }
            val x=n.cx-n.x; val y=n.cy-n.y
            val opening=if (theta > 0) n.width/theta+sqrt(x*x+y*y) else Double.POSITIVE_INFINITY
            n.openingSquared=opening*opening
        }
        for (i in 0 until count) {
            if (exactTargets[i]) for (other in 0 until count) {
                if (other != i) add(i,state[other*4],state[other*4+1],masses[other],min(smoothing[i],smoothing[other]),acceleration,rates)
            } else visit(0,i,acceleration,rates,travel)
        }
        // Standard monopole BH is not pairwise symmetric. Remove the tiny common acceleration
        // so an isolated freely moving scene retains its centre-of-mass momentum.
        if (conserveMomentum) {
            var mass=0.0; var ax=0.0; var ay=0.0
            for (i in 0 until count) { mass+=weights[i]; ax+=weights[i]*acceleration[i*2]; ay+=weights[i]*acceleration[i*2+1] }
            if (mass > 0) for (i in 0 until count) { acceleration[i*2]-=ax/mass; acceleration[i*2+1]-=ay/mass }
        }
    }
    private fun add(i: Int,x: Double,y: Double,mass: Double,soft: Double,out: DoubleArray,rates: DoubleArray?): Double {
        if (mass <= 0) return 0.0
        interactions++
        val dx=x-state[i*4]; val dy=y-state[i*4+1]
        val square=dx*dx+dy*dy+soft*soft
        val factor=400.0*mass/(square*sqrt(square))
        out[i*2]+=dx*factor; out[i*2+1]+=dy*factor
        if (rates != null) rates[i]+=factor
        return sqrt(square)
    }
    private fun visit(index: Int,i: Int,out: DoubleArray,rates: DoubleArray?,travel: DoubleArray?) {
        val n=nodes[index]
        if (n.total <= 0) return
        if (n.head >= 0) {
            var other=n.head
            while (other >= 0) {
                if (other != i) {
                    val distance=add(i,state[other*4],state[other*4+1],masses[other],min(smoothing[i],smoothing[other]),out,rates)
                    if (travel != null) {
                        val vx=state[other*4+2]-state[i*4+2]; val vy=state[other*4+3]-state[i*4+3]
                        val speed=hypot(vx,vy)
                        if (speed > 1e-9) travel[i]=min(travel[i],.1*distance/speed)
                    }
                }
                other=links[other]
            }
            return
        }
        val dx=n.cx-state[i*4]; val dy=n.cy-state[i*4+1]
        val contains=abs(state[i*4]-n.x) <= n.width*.5 && abs(state[i*4+1]-n.y) <= n.width*.5
        if (!contains && dx*dx+dy*dy > n.openingSquared) {
            if (n.mass > 0) add(i,n.nx,n.ny,n.mass,smoothing[i],out,rates)
            if (n.physical > 0) add(i,n.physicalX,n.physicalY,n.physical,.01,out,rates)
        } else for (child in n.children) if (child >= 0) visit(child,i,out,rates,travel)
    }

    companion object {
        private class Estimate(val count: Int,val tree: BarnesHutGravity) {
            val state=DoubleArray(count*4); val mass=DoubleArray(count)
            val soft=DoubleArray(count); val acceleration=DoubleArray(count*2)
            val rates=DoubleArray(count); val travel=DoubleArray(count)
        }
        private val estimate=ThreadLocal<Estimate>()
        fun stepLimit(bodies: List<CelestialBody>): Double {
            val work=estimate.get()?.takeIf { it.count == bodies.size } ?: Estimate(bodies.size,estimate.get()?.tree ?: BarnesHutGravity()).also(estimate::set)
            var physical=false
            bodies.forEachIndexed { i,b ->
                work.state[i*4]=b.position.x; work.state[i*4+1]=b.position.y
                work.state[i*4+2]=b.velocity.x; work.state[i*4+3]=b.velocity.y
                work.mass[i]=b.gravityMass; work.soft[i]=if (b.physicalScale) .01 else 18.0
                physical=physical || b.physicalScale
            }
            work.tree.compute(work.state,work.mass,work.soft,work.acceleration,work.rates,work.travel)
            val fraction=if (physical) .2 else .05
            val gravity=fraction/sqrt(work.rates.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12)
            return min(gravity,work.travel.minOrNull() ?: 1.0/30).coerceIn(if (physical) 1.0/20000 else 1.0/240,1.0/30)
        }
    }
}
