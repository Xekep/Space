package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xekep.space.sim.Vec2
import kotlin.math.*

private data class LootVertex(val x: Double,val y: Double,val z: Double) {
    operator fun minus(other: LootVertex)=LootVertex(x-other.x,y-other.y,z-other.z)
    fun cross(other: LootVertex)=LootVertex(y*other.z-z*other.y,z*other.x-x*other.z,x*other.y-y*other.x)
    fun dot(other: LootVertex)=x*other.x+y*other.y+z*other.z
    fun normalized(): LootVertex { val length=sqrt(dot(this)).coerceAtLeast(1e-9);return LootVertex(x/length,y/length,z/length) }
}
private data class LootFace(val vertices: List<LootVertex>,val color: Color)
private val Steel=Color(0xFF537A91)
private val CargoLight=Color(0xFF9EF8FF)

/** Slow, deterministic world drift needs no new save fields or random draws. */
internal fun lootDriftVelocity(seed: Int): Vec2 {
    val angle=seed*2.3999632297
    return Vec2(cos(angle),sin(angle))*6.0
}
internal fun SideEventKind.lootModel()=when(this) {
    SideEventKind.Supply -> LootAppearance.Container
    SideEventKind.Wreck -> LootAppearance.Module
    SideEventKind.Rescue -> LootAppearance.Capsule
}

private class LootMesh {
    val faces=mutableListOf<LootFace>()
    fun box(x: Double,y: Double,z: Double,hx: Double,hy: Double,hz: Double,color: Color) {
        val v=listOf(LootVertex(x-hx,y-hy,z-hz),LootVertex(x+hx,y-hy,z-hz),LootVertex(x+hx,y+hy,z-hz),LootVertex(x-hx,y+hy,z-hz),
            LootVertex(x-hx,y-hy,z+hz),LootVertex(x+hx,y-hy,z+hz),LootVertex(x+hx,y+hy,z+hz),LootVertex(x-hx,y+hy,z+hz))
        for (indices in listOf(listOf(4,5,6,7),listOf(0,3,2,1),listOf(1,2,6,5),listOf(0,4,7,3),listOf(0,1,5,4),listOf(3,7,6,2)))
            faces+=LootFace(indices.map { v[it] },color)
    }
    fun pod(radius: Double,halfLength: Double,color: Color) {
        val v=(0..1).flatMap { end -> (0..7).map { i ->
            val angle=i*PI/4;LootVertex(cos(angle)*radius,if (end==0) -halfLength else halfLength,sin(angle)*radius) } }
        for (i in 0..7) { val next=(i+1)%8;faces+=LootFace(listOf(v[i],v[i+8],v[next+8],v[next]),color) }
        faces+=LootFace(v.take(8),color);faces+=LootFace(v.drop(8).reversed(),color)
    }
}

/** Cached solid geometry: panels and ribs are actual faces, not a flat sticker. */
private val lootMeshes=LootAppearance.entries.associateWith { kind -> LootMesh().apply {
    when(kind) {
        LootAppearance.Container -> {
            box(0.0,0.0,0.0,.72,.66,.60,Steel)
            val band=Color(0xFF63C4D0)
            for (y in listOf(-.4,.4)) box(0.0,y,0.0,.76,.07,.64,band)
            box(0.0,0.0,.64,.36,.28,.04,Color(0xFF162F47))
            box(0.0,0.0,.70,.18,.13,.02,CargoLight)
            for (x in listOf(-.64,.64)) for (y in listOf(-.58,.58)) box(x,y,.65,.06,.06,.04,Color(0xFFC0D1D5))
            box(.75,0.0,0.0,.03,.25,.30,Color(0xFF23475D))
        }
        LootAppearance.Module -> {
            pod(.65,.7,Color(0xFF596C80))
            box(0.0,.72,0.0,.44,.12,.44,Color(0xFF182936))
            box(0.0,-.72,0.0,.40,.05,.40,Color(0xFFAF8770))
            box(-.66,.10,.10,.12,.5,.18,Color(0xFF304858))
            box(.72,.32,.10,.34,.08,.40,Color(0xFF98AABA))
            box(0.0,0.0,.66,.24,.20,.05,Color(0xFF183F56))
            box(-.13,-.09,.72,.05,.12,.015,Color(0xFFFFBC69))
            box(.10,.09,.72,.10,.035,.015,Color(0xFF81CAD6))
        }
        LootAppearance.Capsule -> {
            pod(.55,.85,Color(0xFFB7C9CE))
            for (y in listOf(-.60,.60)) box(0.0,y,0.0,.58,.07,.58,Color(0xFFDD9C57))
            box(0.0,-.12,.57,.27,.30,.04,Color(0xFF142D46))
            box(0.0,-.16,.62,.19,.18,.012,Color(0xFF62DCE7))
            for (x in listOf(-.7,.7)) box(x,.48,0.0,.10,.22,.33,Color(0xFF405E79))
        }
    }
}.faces.toList() }

internal fun DrawScope.drawLootAppearance(kind: LootAppearance,center: Offset,radius: Float,time: Double,animate: Boolean=true) {
    if (radius<=0 || !radius.isFinite()) return
    // Neutral pose keeps volume and role readable when decorative motion is disabled.
    val phase=kind.ordinal*.8
    val yaw=.55+if (animate) time*.23+phase else phase
    val pitch=.48+if (animate) .14*sin(time*.37+phase) else 0.0
    val roll=if (animate) .18*sin(time*.21+phase) else -.12
    val cy=cos(yaw);val sy=sin(yaw);val cp=cos(pitch);val sp=sin(pitch);val cr=cos(roll);val sr=sin(roll)
    fun rotate(v: LootVertex): LootVertex {
        val x=v.x*cy+v.z*sy;val z=-v.x*sy+v.z*cy
        val y=v.y*cp-z*sp;val depth=v.y*sp+z*cp
        return LootVertex(x*cr-y*sr,x*sr+y*cr,depth)
    }
    fun project(v: LootVertex): Offset { val w=(1-v.z/4.5).coerceAtLeast(.3);return center+Offset((v.x/w*radius).toFloat(),(v.y/w*radius).toFloat()) }
    val light=LootVertex(-.4,-.55,.75).normalized()
    val path=Path()
    drawCircle(CargoLight.copy(alpha=.055f),radius*1.45f,center)
    val faces=lootMeshes.getValue(kind).map { face -> LootFace(face.vertices.map(::rotate),face.color) }
        .sortedBy { it.vertices.sumOf { v -> v.z }/it.vertices.size }
    for (face in faces) {
        val vertices=face.vertices
        val normal=(vertices[1]-vertices[0]).cross(vertices[2]-vertices[0]).normalized()
        val centroid=LootVertex(vertices.sumOf { it.x }/vertices.size,vertices.sumOf { it.y }/vertices.size,vertices.sumOf { it.z }/vertices.size)
        if (normal.dot(LootVertex(0.0,0.0,4.5)-centroid)<=0) continue
        val shade=(.42+.58*maxOf(0.0,normal.dot(light))).toFloat()
        val color=face.color
        path.reset();vertices.forEachIndexed { index,v -> val p=project(v);if (index==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y) };path.close()
        drawPath(path,Color(color.red*shade,color.green*shade,color.blue*shade,color.alpha))
        drawPath(path,Color.White.copy(alpha=.16f),style=Stroke(.7.dp.toPx()))
    }
}

internal fun DrawScope.drawWorldLoot(kind: LootAppearance,position: Vec2,game: SpaceGameState,camera: SpaceCamera,time: Double,animate: Boolean) {
    // SpaceScene's world Canvas supplies camera rotation exactly once.
    val point=worldToScreen(position,game.viewport,camera.center,camera.zoom)
    val radius=(22*camera.zoom).coerceIn(12.dp.toPx(),32.dp.toPx())
    if (point.x < -radius*2 || point.y < -radius*2 || point.x > size.width+radius*2 || point.y > size.height+radius*2) return
    drawLootAppearance(kind,point,radius,time,animate)
}
