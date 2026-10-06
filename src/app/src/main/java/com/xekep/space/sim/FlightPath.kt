package com.xekep.space.sim

/** Interpolating Hermite spline. The arc-length table is immutable and built once per route. */
data class FlightPath(val knots: List<Vec2>) {
    init { require(knots.size in 2..MAX_WAYPOINTS + 1) }

    data class Sample(val position: Vec2, val direction: Vec2)
    private data class Entry(val distance: Double, val segment: Int, val t: Double, val position: Vec2)
    private val tangents = knots.indices.map { i ->
        val incoming = knots[i] - knots[if (i > 0) i - 1 else 0]
        val outgoing = knots[if (i < knots.lastIndex) i + 1 else i] - knots[i]
        when (i) {
            0 -> outgoing
            knots.lastIndex -> incoming
            else -> {
                val sum = incoming.normalized() + outgoing.normalized()
                // A deliberate U-turn needs a rounded loop, rather than a zero tangent/stall.
                val direction = if (sum.magnitude() > 1e-6) sum.normalized() else incoming.normalized().perpendicular()
                direction * minOf(incoming.magnitude(), outgoing.magnitude())
            }
        }
    }
    private fun curve(segment: Int, t: Double): Sample {
        val a = knots[segment]; val b = knots[segment + 1]
        val m = tangents[segment]; val n = tangents[segment + 1]
        val t2 = t * t; val t3 = t2 * t
        return Sample(a * (2*t3-3*t2+1) + m * (t3-2*t2+t) + b * (-2*t3+3*t2) + n * (t3-t2),
            (a * (6*t2-6*t) + m * (3*t2-4*t+1) + b * (-6*t2+6*t) + n * (3*t2-2*t)).normalized())
    }
    private val entries = buildList<Entry> {
        add(Entry(0.0, 0, 0.0, knots.first()))
        for (segment in 0 until knots.lastIndex) for (step in 1..64) {
            val t = step / 64.0
            val point = curve(segment, t).position
            add(Entry(last().distance + (point-last().position).magnitude(), segment, t, point))
        }
    }
    val length: Double get() = entries.last().distance
    val waypointDistances: List<Double> = (1..knots.lastIndex).map { entries[it * 64].distance }

    fun sample(distance: Double): Sample {
        if (distance >= length) {
            val end = curve(knots.lastIndex-1, 1.0)
            return end.copy(position = end.position + end.direction * (distance-length))
        }
        val target = distance.coerceAtLeast(0.0)
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

    fun remainingPoints(distance: Double): List<Vec2> = knots.drop(1+waypointDistances.count { it <= distance+1e-9 })
    fun drawingPoints(distance: Double): List<Vec2> = listOf(sample(distance).position) + entries.filter { it.distance > distance }.map { it.position }

    companion object {
        fun through(start: Vec2, points: List<Vec2>): FlightPath? {
            val unique = mutableListOf(start)
            points.take(MAX_WAYPOINTS).forEach { if ((it-unique.last()).magnitude() > 1e-8) unique += it }
            return unique.takeIf { it.size > 1 }?.let { FlightPath(it.toList()) }
        }
    }
}
