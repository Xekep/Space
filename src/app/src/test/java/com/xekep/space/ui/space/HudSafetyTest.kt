package com.xekep.space.ui.space

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xekep.space.sim.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class HudSafetyTest {
    @Test fun indicatorsRespectMeasuredPanelsAndSceneOriginRatherThanAFixedMargin() {
        val bounds=indicatorBounds(Rect(24f,160f,976f,640f),Offset(0f,100f),
            Rect(0f,100f,1000f,220f),Rect(0f,430f,1000f,900f),14f)
        assertEquals(134f,bounds.top,0f); assertEquals(316f,bounds.bottom,0f)
        for (point in listOf(Offset(500f,-1000f),Offset(500f,2000f),Offset(-1000f,400f),Offset(2000f,400f))) {
            val marker=coreDirectionMarker(point,10f,IntSize(1000,800),bounds)!!
            assertTrue(marker.position.y in bounds.top..bounds.bottom)
            assertTrue(marker.position.x in bounds.left..bounds.right)
            val toward=point-marker.position
            assertTrue(toward.x*marker.direction.x+toward.y*marker.direction.y > 0)
            if (point.x < 0f) assertEquals(bounds.left,marker.position.x,1e-3f)
            if (point.x > 1000f) assertEquals(bounds.right,marker.position.x,1e-3f)
        }
    }
    @Test fun transientOverlappingPanelsHideIndicatorsInsteadOfProducingInvalidGeometry() {
        val bounds=indicatorBounds(Rect(24f,40f,976f,760f),Offset.Zero,
            Rect(0f,0f,1000f,500f),Rect(0f,400f,1000f,800f),14f)
        assertEquals(Rect.Zero,bounds)
        assertNull(coreDirectionMarker(Offset(2000f,400f),10f,IntSize(1000,800),bounds))
    }
    @Test fun restCanOnlyPromiseClearSkiesAfterBothLiveAndAnnouncedThreatsAreGone() {
        val core=CelestialBody(1,Vec2.Zero,Vec2.Zero,8000.0,48f,Color.Cyan,BodyKind.Core)
        val threat=core.copy(id=2,position=Vec2(1000.0,0.0),kind=BodyKind.Meteor)
        val run=ArcadeSession(listOf(core),SpaceCamera(),IntSize(900,1400),ArcadeDifficulty.Normal,elapsed=24.5)
        assertTrue(run.resting); assertTrue(run.threatsCleared)
        assertFalse(run.copy(bodies=listOf(core,threat)).threatsCleared)
        assertFalse(run.copy(pending=listOf(PendingThreat(threat,.3))).threatsCleared)
    }
    @Test fun compactScoreKeepsLargeNumbersReadableWithLocaleAppropriateDecimals() {
        assertEquals("9999",compactScore(9999.0,Locale.US))
        assertEquals("123k",compactScore(123456.0,Locale.US))
        assertEquals("1.2M",compactScore(1234567.0,Locale.US))
        assertEquals("1,2M",compactScore(1234567.0,Locale.GERMANY))
    }
}
