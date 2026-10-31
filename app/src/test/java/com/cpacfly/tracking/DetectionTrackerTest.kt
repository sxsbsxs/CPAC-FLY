package com.cpacfly.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DetectionTrackerTest {
    private fun detection(left: Float, label: String = "airplane") =
        DetectionCandidate(
            box = DetectionBox(left, 10f, left + 30f, 30f),
            label = label,
            score = 0.9f,
            categoryIndex = 4
        )

    @Test
    fun keepsTrackIdAcrossOverlappingFrames() {
        val tracker = DetectionTracker()
        val first = tracker.update(listOf(detection(10f))).single()
        val second = tracker.update(listOf(detection(13f))).single()
        assertEquals(first.trackId, second.trackId)
    }

    @Test
    fun createsNewTrackForDifferentObjectClass() {
        val tracker = DetectionTracker()
        val first = tracker.update(listOf(detection(10f, "airplane"))).single()
        val second = tracker.update(listOf(detection(10f, "bird"))).single()
        assertNotEquals(first.trackId, second.trackId)
    }

    @Test
    fun resetsTrackIds() {
        val tracker = DetectionTracker()
        tracker.update(listOf(detection(10f)))
        tracker.reset()
        assertEquals(1L, tracker.update(listOf(detection(10f))).single().trackId)
    }
    @Test
    fun preservesTrackWhenMlKitTrackingIdIsStableAcrossLabelChanges() {
        val tracker = DetectionTracker()
        val first = tracker.update(
            listOf(detection(10f, "unknown").copy(sourceTrackingId = 42))
        ).single()
        val second = tracker.update(
            listOf(detection(14f, "home goods").copy(sourceTrackingId = 42))
        ).single()
        assertEquals(first.trackId, second.trackId)
    }

    @Test
    fun differentMlKitTrackingIdsDoNotMergeEvenWhenBoxesOverlap() {
        val tracker = DetectionTracker()
        val first = tracker.update(
            listOf(detection(10f).copy(sourceTrackingId = 1))
        ).single()
        val second = tracker.update(
            listOf(detection(10f).copy(sourceTrackingId = 2))
        ).single()
        assertNotEquals(first.trackId, second.trackId)
    }

}
