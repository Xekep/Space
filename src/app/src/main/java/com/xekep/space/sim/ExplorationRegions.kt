package com.xekep.space.sim

import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

const val EXPLORATION_REGION_SIZE=3200.0
const val MAX_EXPLORATION_REGIONS=96
const val MAX_ACTIVE_EXPLORATION_BODIES=48
const val MAX_STORED_EXPLORATION_BODIES=1600
data class ExplorationState(val seed: Long,val origin: Vec2,val visited: Set<Long> = emptySet())
internal data class ExplorationResult(val bodies: List<CelestialBody>,val state: ExplorationState,val added: Boolean=false)
internal data class RegionalFrame(val active: List<CelestialBody>,val dormant: List<CelestialBody>) {
    fun combine(next: List<CelestialBody>)=if (dormant.isEmpty()) next else next+dormant
}
internal fun regionKey(x: Int,y: Int): Long=(x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
internal fun regionCoordinates(key: Long)=Pair((key shr 32).toInt(),key.toInt())
internal fun validRegionKey(key: Long)=regionCoordinates(key).let { (x,y) -> x in -65536..65536 && y in -65536..65536 }
private fun regionCenter(key: Long,origin: Vec2)=regionCoordinates(key).let { (x,y) -> origin+Vec2(x*EXPLORATION_REGION_SIZE,y*EXPLORATION_REGION_SIZE) }

// Covers every orbit, metal cluster and stellar halo; test the bound when changing layouts.
private const val RegionFootprintRadius=1200.0
private fun regionRandom(state: ExplorationState,key: Long)=Random(state.seed xor (key*-7046029254386353131L))
private fun regionAnchor(state: ExplorationState,key: Long): Vec2 {
    val nominal=regionCenter(key,state.origin);val radial=nominal-state.origin
    return if (radial.magnitude()<3800) state.origin+radial.normalized()*3800.0 else nominal
}
internal fun explorationRegionCenter(state: ExplorationState,key: Long): Vec2 {
    val random=regionRandom(state,key)
    return regionAnchor(state,key)+Vec2(random.nextDouble(-280.0,280.0),random.nextDouble(-280.0,280.0))
}
private fun cameraDistance(point: Vec2,focus: Vec2,halfView: Vec2,rotation: Double): Double {
    val delta=point-focus;val c=cos(rotation);val s=sin(rotation)
    return hypot(max(0.0,abs(delta.x*c-delta.y*s)-halfView.x),max(0.0,abs(delta.x*s+delta.y*c)-halfView.y))
}

/** Preload entire regions outside the rotated viewport, never materialize them on screen.
 * Visible unvisited cells stay unvisited: moving away can safely preload them for a return. */
internal fun exploreRegions(bodies: List<CelestialBody>,state: ExplorationState,focus: Vec2,halfView: Vec2,rotation: Double=0.0): ExplorationResult {
    if (!focus.x.isFinite() || !focus.y.isFinite() || !halfView.x.isFinite() || !halfView.y.isFinite() ||
        halfView.x<=0 || halfView.y<=0 || !rotation.isFinite() ||
        (focus-state.origin).magnitude()+halfView.magnitude()<2400 ||
        state.visited.size>=MAX_EXPLORATION_REGIONS || bodies.size>MAX_SANDBOX_BODIES-20 ||
        bodies.count { it.explorationRegion!=null }>MAX_STORED_EXPLORATION_BODIES-20) return ExplorationResult(bodies,state)
    val margin=max(200.0,min(halfView.x,halfView.y)*.15)
    val reach=halfView+Vec2(RegionFootprintRadius+margin,RegionFootprintRadius+margin)
    val candidates=mutableSetOf<Long>();val c=cos(rotation);val s=sin(rotation)
    for (dx in -1..1) for (dy in -1..1) if (dx!=0 || dy!=0) {
        val screen=Vec2(dx*reach.x,dy*reach.y)
        val local=focus+Vec2(screen.x*c+screen.y*s,-screen.x*s+screen.y*c)-state.origin
        val x=floor(local.x/EXPLORATION_REGION_SIZE+.5).toInt();val y=floor(local.y/EXPLORATION_REGION_SIZE+.5).toInt()
        if (x in -65535..65535 && y in -65535..65535)
            for (ox in -1..1) for (oy in -1..1) candidates+=regionKey(x+ox,y+oy)
    }
    val key=candidates.asSequence().filter { it !in state.visited && (regionCenter(it,state.origin)-state.origin).magnitude()>2700 }
        .map { it to cameraDistance(explorationRegionCenter(state,it),focus,halfView,rotation) }
        .filter { it.second>RegionFootprintRadius+margin && it.second<RegionFootprintRadius+margin+EXPLORATION_REGION_SIZE*1.5 }
        .minByOrNull { it.second }?.first ?: return ExplorationResult(bodies,state)
    val generated=createExplorationRegion(state,key)
    if (generated.any { new -> bodies.any { (it.position-new.position).magnitude()<it.radius+new.radius+10 } })
        return ExplorationResult(bodies,state.copy(visited=state.visited+key))
    return ExplorationResult(bodies+generated,state.copy(visited=state.visited+key),true)
}

/** Real, varied bodies: compact planetary systems and loose fields of metal fragments. */
internal fun createExplorationRegion(state: ExplorationState,key: Long): List<CelestialBody> {
    val random=regionRandom(state,key)
    val center=regionAnchor(state,key)+Vec2(random.nextDouble(-280.0,280.0),random.nextDouble(-280.0,280.0))
    val bodies=mutableListOf<CelestialBody>()
    fun body(position: Vec2,velocity: Vec2,mass: Double,radius: Float,color: Color,kind: BodyKind=BodyKind.Ambient,debris: Boolean=false,parent: Long?=null): CelestialBody {
        val angle=random.nextDouble(2*PI)
        return CelestialBody(SimulationEngine.newBodyId(),position,velocity,mass,radius,color,kind,
            heading=Vec2(cos(angle),sin(angle)),isDebris=debris,orbitParentId=parent,explorationRegion=key)
    }
    if (random.nextInt(4)!=0) {
        val mass=random.nextDouble(2600.0,6000.0)
        val colors=listOf(Color(0xFFFFCA83),Color(0xFFF99867),Color(0xFFABD8FF),Color(0xFFFFE9BD))
        val star=body(center,Vec2.Zero,mass,random.nextDouble(30.0,51.0).toFloat(),colors[random.nextInt(colors.size)],BodyKind.Star)
        bodies+=star
        val planets=random.nextInt(3,6)
        repeat(planets) { index ->
            val distance=170.0+index*110+random.nextDouble(0.0,30.0)
            val phase=random.nextDouble(2*PI);val radial=Vec2(cos(phase),sin(phase))
            val position=center+radial*distance
            val planet=body(position,radial.perpendicular()*SimulationEngine.orbitVelocity(star,position).magnitude(),
                random.nextDouble(12.0,85.0),random.nextDouble(4.0,13.0).toFloat(),
                listOf(Color(0xFF91BBCE),Color(0xFFD2A379),Color(0xFFB9C794),Color(0xFFB2A2CE))[random.nextInt(4)],parent=star.id)
            bodies+=planet
            val hillLimit=.35*distance*cbrt(planet.mass/(3*star.mass))
            if (index>=2 && index%2==0 && planet.mass>30 && hillLimit>planet.radius+8) {
                val satelliteRadius=minOf(planet.radius*3.2+14,hillLimit*.9)
                val moonRadial=Vec2(cos(phase+1.3),sin(phase+1.3))
                val moonPosition=position+moonRadial*satelliteRadius
                bodies+=body(moonPosition,planet.velocity+moonRadial.perpendicular()*(SimulationEngine.orbitVelocity(planet,moonPosition)-planet.velocity).magnitude(),
                    random.nextDouble(.2,1.5),random.nextDouble(1.1,2.5).toFloat(),Color(0xFFBCBDC5),parent=planet.id)
            }
        }
    }
    val cluster=center+Vec2(random.nextDouble(600.0,900.0),random.nextDouble(-450.0,450.0))
    repeat(if (bodies.isEmpty()) random.nextInt(10,18) else random.nextInt(4,8)) {
        for (attempt in 0 until 20) {
            val clump=if (it%2==0) cluster else cluster+Vec2(-150.0,90.0)
            val point=clump+Vec2(random.nextDouble(-110.0,110.0),random.nextDouble(-80.0,80.0))
            val radius=random.nextDouble(1.5,5.0).toFloat()
            if (bodies.none { (it.position-point).magnitude()<it.radius+radius+8 }) {
                bodies+=body(point,Vec2(random.nextDouble(-3.0,3.0),random.nextDouble(-3.0,3.0)),random.nextDouble(.4,4.0),radius,
                    listOf(Color(0xFF98B0BF),Color(0xFFC4ADA0),Color(0xFF879AAB))[random.nextInt(3)],debris=true)
                break
            }
            if (attempt==19) break
        }
    }
    return bodies.take(20)
}

/** Keep stored regions intact; only nearby regions enter the expensive solver. */
internal fun regionalFrame(bodies: List<CelestialBody>,state: ExplorationState?,focus: Vec2): RegionalFrame {
    if (state==null || bodies.none { it.explorationRegion!=null }) return RegionalFrame(bodies,emptyList())
    val visitors=bodies.filter { it.explorationRegion==null && it.kind!=BodyKind.Core }
    val groups=bodies.filter { it.explorationRegion!=null }.groupBy { it.explorationRegion!! }
    val centers=groups.mapValues { (_,items) -> items.firstOrNull { it.kind==BodyKind.Star }?.position ?:
        Vec2(items.sumOf { it.position.x }/items.size,items.sumOf { it.position.y }/items.size) }
    val priority=groups.keys.sortedBy { key ->
        val center=centers.getValue(key)
        val visitor=visitors.minOfOrNull { (it.position-center).magnitude() } ?: Double.POSITIVE_INFINITY
        if (visitor<1900) visitor-1e8 else (center-focus).magnitude()
    }
    val active=mutableSetOf<Long>();var count=0
    for (key in priority) if (count+groups.getValue(key).size<=MAX_ACTIVE_EXPLORATION_BODIES &&
        ((centers.getValue(key)-focus).magnitude()<6400 || visitors.any { (it.position-centers.getValue(key)).magnitude()<1900 })) {
        active+=key;count+=groups.getValue(key).size
    }
    return RegionalFrame(bodies.filter { it.explorationRegion==null || it.explorationRegion in active },
        bodies.filter { it.explorationRegion!=null && it.explorationRegion !in active })
}


/** Independent regional gravity domains keep distant scenery from changing core defence.
 * Local visitors join the nearest system; core-bound hostile waves stay in the main domain. */
internal fun explorationGravityGroups(bodies: List<CelestialBody>,fixedCore: Boolean): List<List<CelestialBody>> {
    val regional=bodies.filter { it.explorationRegion!=null }
    if (regional.isEmpty()) return listOf(bodies)
    return bodies.groupBy { body ->
        body.explorationRegion ?: if (body.kind==BodyKind.Core || fixedCore && body.kind==BodyKind.Meteor) Long.MIN_VALUE else {
            val near=regional.minByOrNull { (it.position-body.position).magnitude() }
            if (near!=null && (near.position-body.position).magnitude()<1500) near.explorationRegion!! else Long.MIN_VALUE
        }
    }.values.toList()
}
