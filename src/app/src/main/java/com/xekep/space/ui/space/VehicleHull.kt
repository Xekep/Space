package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.xekep.space.sim.*
import kotlin.math.*

internal data class HullVertex(val x: Float,val y: Float,val z: Float,val exhaust: Boolean=false) {
    operator fun minus(b: HullVertex)=HullVertex(x-b.x,y-b.y,z-b.z)
    fun cross(b: HullVertex)=HullVertex(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun dot(b: HullVertex)=x*b.x+y*b.y+z*b.z
    fun normalized(): HullVertex { val n=sqrt(dot(this)); return if (n > 1e-6f) HullVertex(x/n,y/n,z/n) else HullVertex(0f,0f,0f) }
}
internal enum class HullMaterial { Armor, Panel, Glass, Trim, Engine, Flame, FlameCore }
internal data class HullFace(val indices: IntArray,val normal: HullVertex,val material: HullMaterial)
internal data class VehicleMesh(val vertices: List<HullVertex>,val faces: List<HullFace>,val nozzles: List<HullVertex>)

private class HullBuilder {
    val vertices=ArrayList<HullVertex>(); val faces=ArrayList<HullFace>(); val nozzles=ArrayList<HullVertex>()
    fun vertex(v: HullVertex)=vertices.size.also { vertices+=v }
    fun face(indices: List<Int>,outward: HullVertex,material: HullMaterial) {
        val ids=indices.distinct(); if (ids.size < 3) return
        val normal=(vertices[ids[1]]-vertices[ids[0]]).cross(vertices[ids[2]]-vertices[ids[0]])
        if (normal.dot(normal) < 1e-10f) return
        val ordered=if (normal.dot(outward) >= 0) ids else ids.reversed()
        faces+=HullFace(ordered.toIntArray(),(vertices[ordered[1]]-vertices[ordered[0]]).cross(vertices[ordered[2]]-vertices[ordered[0]]).normalized(),material)
    }
    fun tube(x: Float,z: Float,stations: List<Triple<Float,Float,Float>>,sides: Int,material: HullMaterial) {
        val rings=stations.map { (y,rx,rz) ->
            if (rx == 0f && rz == 0f) { val tip=vertex(HullVertex(x,y,z)); List(sides) { tip } }
            else List(sides) { i -> val a=2*PI*(i+.5)/sides; vertex(HullVertex(x+rx*cos(a).toFloat(),y,z+rz*sin(a).toFloat())) }
        }
        face(rings.first(),HullVertex(0f,-1f,0f),material)
        face(rings.last(),HullVertex(0f,1f,0f),material)
        for (row in 0 until rings.lastIndex) for (i in 0 until sides) {
            val next=(i+1)%sides; val a=2*PI*(i+1)/sides
            face(listOf(rings[row][i],rings[row][next],rings[row+1][next],rings[row+1][i]),HullVertex(cos(a).toFloat(),0f,sin(a).toFloat()),material)
        }
    }
    fun prism(points: List<Offset>,bottom: Float,top: Float,material: HullMaterial) {
        val winding=if (points.indices.sumOf { i -> val a=points[i]; val b=points[(i+1)%points.size]; (a.x*b.y-a.y*b.x).toDouble() } >= 0) 1f else -1f
        val low=points.map { vertex(HullVertex(it.x,it.y,bottom)) }; val high=points.map { vertex(HullVertex(it.x,it.y,top)) }
        face(high,HullVertex(0f,0f,1f),material); face(low,HullVertex(0f,0f,-1f),material)
        for (i in points.indices) { val j=(i+1)%points.size; val edge=points[j]-points[i]
            face(listOf(high[i],low[i],low[j],high[j]),HullVertex(edge.y*winding,-edge.x*winding,0f),material)
        }
    }
    fun engine(x: Float,y: Float,z: Float,r: Float,sides: Int=6) {
        tube(x,z,listOf(Triple(y-.14f,r*.85f,r*.85f),Triple(y,r,r)),sides,HullMaterial.Engine)
        nozzles+=HullVertex(x,y,z)
        for ((core,radius) in listOf(false to r*.85f,true to r*.40f)) {
            val ring=List(4) { i -> val a=2*PI*(i+.5)/4; vertex(HullVertex(x+radius*cos(a).toFloat(),y+.01f,z+radius*sin(a).toFloat())) }
            val tip=vertex(HullVertex(x,y+(if (core) .35f else .65f),z,exhaust=true))
            for (i in ring.indices) { val a=2*PI*(i+1)/4
                face(listOf(ring[i],ring[(i+1)%4],tip),HullVertex(cos(a).toFloat(),0f,sin(a).toFloat()),if (core) HullMaterial.FlameCore else HullMaterial.Flame)
            }
        }
    }
    fun build()=VehicleMesh(vertices.toList(),faces.toList(),nozzles.toList())
}
private fun polygon(vararg xy: Float)=xy.toList().chunked(2).map { Offset(it[0],it[1]) }
private fun rocketMesh(heavy: Boolean): VehicleMesh = HullBuilder().apply {
    val width=if (heavy) .32f else .24f
    tube(0f,0f,listOf(Triple(-1.22f,0f,0f),Triple(-.57f,width,width),Triple(.68f,width,width)),8,HullMaterial.Armor)
    tube(0f,0f,listOf(Triple(-.57f,width*1.02f,width*1.02f),Triple(-.47f,width*1.02f,width*1.02f)),8,HullMaterial.Trim)
    tube(0f,0f,listOf(Triple(.27f,width*1.03f,width*1.03f),Triple(.35f,width*1.03f,width*1.03f)),8,HullMaterial.Trim)
    for (side in listOf(-1f,1f)) {
        prism(polygon(side*width,.1f,side*.72f,.85f,side*width,.69f),-.04f,.04f,HullMaterial.Panel)
        if (heavy) {
            tube(side*.51f,0f,listOf(Triple(-.40f,0f,0f),Triple(-.18f,.12f,.12f),Triple(.69f,.12f,.12f)),6,HullMaterial.Panel)
            engine(side*.51f,.85f,0f,.12f)
        }
    }
    // The dorsal fin exposes roll; it is a solid vertical wedge, not a painted stripe.
    prism(polygon(-.035f,.20f,.035f,.20f,.035f,.66f,-.035f,.66f),width*.5f,width+ .16f,HullMaterial.Panel)
    engine(0f,.84f,0f,width*.88f,8)
}.build()
private fun shipMesh(guardian: Boolean,heavy: Boolean): VehicleMesh = HullBuilder().apply {
    val width=if (guardian) .42f else if (heavy) .38f else .28f
    tube(0f,0f,listOf(Triple(-1.20f,0f,0f),Triple(-.53f,width,.22f),Triple(.56f,width*.90f,.19f),Triple(.77f,width*.7f,.13f)),4,HullMaterial.Armor)
    // Raised, faceted canopy with a distinct dark roof and side windows.
    tube(0f,.19f,listOf(Triple(-.78f,0f,0f),Triple(-.48f,width*.55f,.14f),Triple(.02f,width*.48f,.10f),Triple(.14f,0f,0f)),4,HullMaterial.Glass)
    for (side in listOf(-1f,1f)) {
        val tip=if (guardian) 1.04f else if (heavy) 1.16f else 1.05f
        prism(polygon(side*width,-.26f,side*tip,.17f,side*(tip-.07f),.74f,side*width,.51f),-.09f,.035f,HullMaterial.Panel)
        prism(polygon(side*(width+.08f),.05f,side*(tip-.15f),.28f,side*(tip-.19f),.39f,side*(width+.08f),.18f),.04f,.065f,HullMaterial.Trim)
        val x=side*(if (heavy) .80f else .72f)
        tube(x,-.025f,listOf(Triple(-.52f,0f,0f),Triple(-.34f,.13f,.14f),Triple(.68f,.13f,.14f)),6,HullMaterial.Panel)
        engine(x,.86f,-.025f,.125f)
        if (heavy) engine(side*.28f,.86f,-.07f,.10f)
        // Gun barrels and vertical tail fins remain visible from every attitude.
        tube(side*(width+.07f),.09f,listOf(Triple(-.65f,.04f,.04f),Triple(-.05f,.04f,.04f)),4,HullMaterial.Engine)
        prism(polygon(x-.035f,.34f,x+.035f,.34f,x+.035f,.69f,x-.035f,.69f),.03f,.26f,HullMaterial.Armor)
    }
}.build()
private val standardRocket by lazy { rocketMesh(false) }
private val heavyRocket by lazy { rocketMesh(true) }
private val standardShip by lazy { shipMesh(false,false) }
private val guardianShip by lazy { shipMesh(true,false) }
private val heavyShip by lazy { shipMesh(false,true) }
internal fun vehicleMesh(body: CelestialBody): VehicleMesh = when {
    body.kind == BodyKind.Rocket -> if (body.hullClass == VehicleHullClass.Heavy) heavyRocket else standardRocket
    body.hullClass == VehicleHullClass.Heavy -> heavyShip
    body.shipClass == ShipClass.Guardian -> guardianShip
    else -> standardShip
}

internal fun vehicleNozzlePoint(body: CelestialBody,radius: Float,aft: Float=0f): Offset {
    val points=vehicleMesh(body).nozzles
    var x=0f; var y=0f
    for (p in points) { val projected=vehicleHullPoint(Offset(p.x*radius,(p.y+aft)*radius),p.z*radius,body.pitch,body.roll,radius); x+=projected.x; y+=projected.y }
    return Offset(x/points.size,y/points.size)
}

private class HullWorkspace {
    var x=FloatArray(0); var y=FloatArray(0); var z=FloatArray(0); var order=IntArray(0); var depth=FloatArray(0)
    val path=Path()
    fun prepare(mesh: VehicleMesh) {
        if (x.size < mesh.vertices.size) { x=FloatArray(mesh.vertices.size); y=FloatArray(x.size); z=FloatArray(x.size) }
        if (order.size < mesh.faces.size) { order=IntArray(mesh.faces.size); depth=FloatArray(order.size) }
    }
}
private val hullWorkspace=ThreadLocal<HullWorkspace>()

/** Solid local meshes with hidden-face removal, depth sorting and directional face lighting.
 * Mesh data are cached; projection buffers and the drawing path are reused. */
internal fun DrawScope.drawVehicleMesh(body: CelestialBody,center: Offset,radius: Float,piloted: Boolean) {
    val mesh=vehicleMesh(body)
    val work=hullWorkspace.get() ?: HullWorkspace().also(hullWorkspace::set); work.prepare(mesh)
    val cr=cos(body.roll).toFloat(); val sr=sin(body.roll).toFloat(); val cp=cos(body.pitch).toFloat(); val sp=sin(body.pitch).toFloat()
    val throttle=if (piloted) body.pilotThrottle.toFloat() else .6f
    mesh.vertices.forEachIndexed { i,v ->
        val localY=v.y+(if (v.exhaust) .70f*throttle else 0f)
        val x=v.x*cr-v.z*sr; val y=localY*cp+(v.x*sr+v.z*cr)*sp; val z=-localY*sp+(v.x*sr+v.z*cr)*cp
        val w=(1-z/4).coerceAtLeast(.15f)
        work.x[i]=center.x+radius*x/w; work.y[i]=center.y+radius*y/w; work.z[i]=z
    }
    var count=0
    mesh.faces.forEachIndexed { i,f ->
        if (!body.enginePowered && (f.material == HullMaterial.Flame || f.material == HullMaterial.FlameCore)) return@forEachIndexed
        val a=f.indices[0]; val b=f.indices[1]; val c=f.indices[2]
        val area=(work.x[b]-work.x[a])*(work.y[c]-work.y[a])-(work.y[b]-work.y[a])*(work.x[c]-work.x[a])
        if (area <= .001f) return@forEachIndexed
        var depth=0f; for (id in f.indices) depth+=work.z[id]; work.depth[i]=depth/f.indices.size
        var index=count
        while (index > 0 && work.depth[work.order[index-1]] > work.depth[i]) { work.order[index]=work.order[index-1]; index-- }
        work.order[index]=i; count++
    }
    val heavy=body.hullClass == VehicleHullClass.Heavy
    for (i in 0 until count) {
        val f=mesh.faces[work.order[i]]; val n=f.normal
        val nx=n.x*cr-n.z*sr; val ny=n.y*cp+(n.x*sr+n.z*cr)*sp; val nz=-n.y*sp+(n.x*sr+n.z*cr)*cp
        val light=(.48f+.52f*(-nx*.32f-ny*.42f+nz*.85f).coerceAtLeast(0f)).coerceIn(.35f,1f)
        val base=when(f.material) {
            HullMaterial.Armor -> if (heavy) Color(0xFFC9D2DE) else Color(0xFFDCECF4)
            HullMaterial.Panel -> if (heavy) Color(0xFF53697E) else Color(0xFF567F9C)
            HullMaterial.Glass -> Color(0xFF248CB0)
            HullMaterial.Trim -> if (heavy) Color(0xFFE6BA74) else body.color
            HullMaterial.Engine -> Color(0xFF273B4C)
            HullMaterial.Flame -> if (body.kind == BodyKind.Rocket) Color(0xFFFF9D52) else Color(0xFF61DDF5)
            HullMaterial.FlameCore -> Color(0xFFE6FBFF)
        }
        val emissive=f.material == HullMaterial.Flame || f.material == HullMaterial.FlameCore
        val color=if (emissive) base else Color(base.red*light,base.green*light,base.blue*light,base.alpha)
        work.path.reset(); f.indices.forEachIndexed { j,id -> if (j == 0) work.path.moveTo(work.x[id],work.y[id]) else work.path.lineTo(work.x[id],work.y[id]) }; work.path.close()
        drawPath(work.path,color)
    }
}
