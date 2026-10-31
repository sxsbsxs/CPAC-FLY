package com.cpacfly.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameTimelineTest {
    @Test
    fun normalizesPtsByRemovingInvalidDuplicatesAndSorting() {
        assertEquals(listOf(0L, 33L, 67L, 100L), VideoFrameTimeline.normalize(listOf(67L, -1L, 33L, 33L, 100L, 0L)))
    }

    @Test
    fun findsNearestPresentationTimestamp() {
        val pts = listOf(0L, 33L, 67L, 100L)
        assertEquals(0, VideoFrameTimeline.nearestFrameIndex(pts, -10L))
        assertEquals(1, VideoFrameTimeline.nearestFrameIndex(pts, 40L))
        assertEquals(2, VideoFrameTimeline.nearestFrameIndex(pts, 50L))
        assertEquals(3, VideoFrameTimeline.nearestFrameIndex(pts, 500L))
    }

    @Test
    fun handlesEmptyTimeline() {
        assertEquals(-1, VideoFrameTimeline.nearestFrameIndex(emptyList(), 0L))
        assertTrue(VideoFrameTimeline.normalize(emptyList()).isEmpty())
    }
}
