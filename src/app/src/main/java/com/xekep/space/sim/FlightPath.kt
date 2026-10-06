package com.xekep.space.sim

/** Interpolating Hermite spline. The arc-length table is immutable and built once per route. */
data class FlightPath(val knots: List<Vec2>, val loopStartIndex: Int? = null) {
    init {
        require(knots.size in 2..MAX_WAYPOINTS + 1)
        require(loopStartIndex == null || (loopStartIndex in 0..1 && loopStartIndex < knots.lastIndex))
    }
    val isLoop: Boolean get() = loopStartIndex != null
    private val geometry = knots + listOfNotNull(loopStartIndex?.let { knots[it] })

    data class Sample(val position: Vec2, val direction: Vec2)
    private data class Entry(val distance: Double, val segment: Int, val t: Double, val position: Vec2)
    private val tangents = knots.indices.map { i ->
        val incoming = knots[i] - knots[if (i == loopStartIndex) knots.lastIndex else (i-1).coerceAtLeast(0)]
        val outgoing = knots[if (i < knots.lastIndex) i+1 else loopStartIndex ?: i] - knots[i]
        when {
            i == 0 && loopStartIndex != 0 -> outgoing
            i == knots.lastIndex && !isLoop -> incoming
            else -> {
                val sum = incoming.normalized() + outgoing.normalized()
                // A deliberate U-turn needs a rounded loop, rather than a zero tangent/stall.
                val direction = if (sum.magnitude() > 1e-6) sum.normalized() else incoming.normalized().perpendicular()
                direction * minOf(incoming.magnitude(), outgoing.magnitude())
            }
        }
    }.let { it + listOfNotNull(loopStartIndex?.let(it::get)) }
    private fun curve(segment: Int, t: Double): Sample {
        val a = geometry[segment]; val b = geometry[segment + 1]
        val m = tangents[segment]; val n = tangents[segment + 1]
        val t2 = t * t; val t3 = t2 * t
        return Sample(a * (2*t3-3*t2+1) + m * (t3-2*t2+t) + b * (-2*t3+3*t2) + n * (t3-t2),
            (a * (6*t2-6*t) + m * (3*t2-4*t+1) + b * (-6*t2+6*t) + n * (3*t2-2*t)).normalized())
    }
    private val entries = buildList<Entry> {
        add(Entry(0.0, 0, 0.0, knots.first()))
        for (segment in 0 until geometry.lastIndex) for (step in 1..64) {
            val t = step / 64.0
            val point = curve(segment, t).position
            add(Entry(last().distance + (point-last().position).magnitude(), segment, t, point))
        }
    }
    val length: Double get() = entries.last().distance
    val waypointDistances: List<Double> = (1..knots.lastIndex).map { entries[it * 64].distance }
    val loopStartDistance: Double get() = entries[(loopStartIndex ?: 0)*64].distance

    fun normalizeDistance(distance: Double): Double = if (isLoop && distance >= length)
        loopStartDistance+(distance-loopStartDistance) % (length-loopStartDistance) else distance.coerceAtLeast(0.0)

    fun sample(distance: Double): Sample {
        if (!isLoop && distance >= length) {
            val end = curve(geometry.lastIndex-1, 1.0)
            return end.copy(position = end.position + end.direction * (distance-length))
        }
        val target = normalizeDistance(distance)
        var low = 0; var high = entries.lastIndex
        while (low+1 < high) {
            val middle = (low+high)/2
            if (entries[middle].distance <= target) low = middle else high = middle
        }
        val a = entries[low]; val b = entries[high]
        val fraction = ((target-a.distance)/(b.distance-a.distance).coerceAtLeast(1e-12)).coerceIn(0.0,1.0)
        val fromT = if (a.segment == b.segment) a.t else 0.0
        return curve(b.segment, fromT+(b.t-fromT)*fraction)
    }

    fun remainingPoints(distance: Double): List<Vec2> = if (isLoop) knots.drop(1) else
        knots.drop(1+waypointDistances.count { it <= distance+1e-9 })
    fun drawingPoints(distance: Double): List<Vec2> = if (isLoop)
        entries.filter { normalizeDistance(distance) < loopStartDistance || it.distance >= loopStartDistance }.map { it.position }
        else listOf(sample(distance).position) + entries.filter { it.distance > distance }.map { it.position }

    companion object {
        fun through(start: Vec2, points: List<Vec2>, forceLoop: Boolean = false, closureTolerance: Double = 1e-8): FlightPath? {
            val unique = mutableListOf(start)
            points.take(MAX_WAYPOINTS).forEach { if ((it-unique.last()).magnitude() > 1e-8) unique += it }
            if (unique.size < 2) return null
            val closesLaunch = unique.size >= 3 && (unique.last()-start).magnitude() <= closureTolerance
            val closesFirstPoint = !closesLaunch && unique.size >= 4 && (unique.last()-unique[1]).magnitude() <= closureTolerance
            val loopStart = when { closesLaunch -> 0; closesFirstPoint -> 1; forceLoop -> 0; else -> null }
            if (closesLaunch || closesFirstPoint) unique.removeAt(unique.lastIndex)
            return FlightPath(unique.toList(),loopStart)
        }
    }
}
