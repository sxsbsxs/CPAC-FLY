package com.cpacfly.tracking

import android.graphics.RectF
import com.cpacfly.detection.MediaPipeObjectDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DetectionTrackerTest {
    private fun detection(left: Float, label: String = "airplane") =
        MediaPipeObjectDetector.Detection(
            box = RectF(left, 10f, left + 30f, 30f),
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
}
