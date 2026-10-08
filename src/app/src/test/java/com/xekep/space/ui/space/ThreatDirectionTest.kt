package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.Vec2
import org.junit.Assert.*
import org.junit.Test

class ThreatDirectionTest {
    private val viewport = IntSize(1000, 800)
    private val bounds = Rect(20f, 40f, 980f, 760f)

    private fun assertPointsAtThreat(point: Offset, marker: ThreatDirectionMarker) {
        val delta = point - marker.position
        val expected = delta / delta.getDistance()
        assertEquals(expected.x, marker.direction.x, 1e-6f)
        assertEquals(expected.y, marker.direction.y, 1e-6f)
        assertTrue(marker.position.x in bounds.left..bounds.right)
        assertTrue(marker.position.y in bounds.top..bounds.bottom)
    }

    @Test fun incomingAndDepartingThreatsAlwaysPointTowardTheObjectAtAllEdges() {
        val points = listOf(Offset(-100f, 400f), Offset(1100f, 400f), Offset(500f, -100f), Offset(500f, 900f))
        val velocities = listOf(Vec2(50.0, 0.0), Vec2(-50.0, 0.0), Vec2(0.0, 50.0), Vec2(0.0, -50.0))
        for (i in points.indices) {
            val incoming = threatDirectionMarker(points[i], velocities[i], 10f, viewport, bounds)!!
            val departing = threatDirectionMarker(points[i], velocities[i] * -1.0, 10f, viewport, bounds)!!
            assertPointsAtThreat(points[i], incoming)
            assertPointsAtThreat(points[i], departing)
            assertEquals(incoming.direction, departing.direction)
        }
    }

    @Test fun keepsThePredictedEntryLocationWhileTheTipPointsBackAtTheThreat() {
        val point = Offset(-100f, 400f)
        val marker = threatDirectionMarker(point, Vec2(50.0, -20.0), 10f, viewport, bounds)!!
        assertEquals(Offset(20f, 360f), marker.position)
        assertPointsAtThreat(point, marker)
        assertTrue(marker.direction.x < 0)
    }

    @Test fun clampingAnEntryAwayFromTheHudStillPointsTowardTheThreat() {
        for ((point, velocity) in listOf(Offset(-100f, -100f) to Vec2(50.0, 50.0),
            Offset(1100f, 900f) to Vec2(-50.0, -50.0), Offset(-100f, 100f) to Vec2(50.0, -40.0))) {
            assertPointsAtThreat(point, threatDirectionMarker(point, velocity, 10f, viewport, bounds)!!)
        }
    }

    @Test fun crossingTheEntryDetectionBoundaryDoesNotReverseTheArrow() {
        val point = Offset(-100f, 400f)
        val entering = threatDirectionMarker(point, Vec2(50.0, 199.9), 10f, viewport, bounds)!!
        val missing = threatDirectionMarker(point, Vec2(50.0, 200.1), 10f, viewport, bounds)!!
        assertPointsAtThreat(point, entering); assertPointsAtThreat(point, missing)
        assertTrue(entering.direction.x * missing.direction.x + entering.direction.y * missing.direction.y > 0)
    }

    @Test fun cameraRotationPanAndZoomKeepPositionAndVelocityInTheSameFrame() {
        val point = Offset(-100f, 400f)
        val camera = Vec2(320.0, -170.0)
        for (zoom in listOf(.15f, 1f, 6f)) for (rotation in listOf(0.0, .7, 1.8, -2.4, Math.PI)) {
            val world = screenToWorld(point, viewport, camera, zoom, rotation)
            val velocity = rotateVector(Vec2(50.0, -20.0) / zoom.toDouble(), -rotation)
            val screen = worldToScreen(world, viewport, camera, zoom, rotation)
            val marker = threatDirectionMarker(screen, rotateVector(velocity * zoom.toDouble(), rotation), 10f, viewport, bounds)!!
            assertEquals(20f, marker.position.x, 1e-4f); assertEquals(360f, marker.position.y, 1e-4f)
            assertPointsAtThreat(screen, marker)
        }
    }

    @Test fun hidesVisibleDisksAndHandlesStationaryAndInvalidInputs() {
        for (point in listOf(Offset(500f, 400f), Offset(10f, 400f), Offset(-9f, 400f), Offset(-6f, -6f)))
            assertNull(threatDirectionMarker(point, Vec2(50.0, 0.0), 10f, viewport, bounds))
        val point = Offset(-100f, 400f)
        assertPointsAtThreat(point, threatDirectionMarker(point, Vec2.Zero, 10f, viewport, bounds)!!)
        assertNull(threatDirectionMarker(Offset(Float.NaN, 0f), Vec2.Zero, 10f, viewport, bounds))
        assertNull(threatDirectionMarker(point, Vec2.Zero, 10f, IntSize.Zero, bounds))
        assertNull(threatDirectionMarker(point, Vec2.Zero, -1f, viewport, bounds))
    }
}
