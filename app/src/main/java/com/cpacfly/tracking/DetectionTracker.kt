package com.cpacfly.tracking

import android.graphics.RectF
import com.cpacfly.detection.MediaPipeObjectDetector

data class TrackedDetection(
    val trackId: Long,
    val detection: MediaPipeObjectDetector.Detection
)

/**
 * Lightweight frame-to-frame IoU association for detector output.
 * It is a generic object tracker, not a paper-airplane recognizer.
 */
class DetectionTracker(
    private val minimumIou: Float = 0.15f,
    private val maxMissedFrames: Int = 5,
    private val smoothing: Float = 0.65f
) {
    private data class Track(
        val id: Long,
        val box: RectF,
        val label: String,
        val missedFrames: Int
    )

    private var nextId = 1L
    private var tracks = mutableListOf<Track>()

    @Synchronized
    fun update(detections: List<MediaPipeObjectDetector.Detection>): List<TrackedDetection> {
        val unmatched = tracks.toMutableList()
        val currentTracks = mutableListOf<Track>()
        val visible = mutableListOf<TrackedDetection>()

        for (detection in detections.sortedByDescending { it.score }) {
            val candidate = unmatched
                .filter { it.label == detection.label }
                .maxByOrNull { iou(it.box, detection.box) }
            val overlap = candidate?.let { iou(it.box, detection.box) } ?: 0f

            val track = if (candidate != null && overlap >= minimumIou) {
                unmatched.remove(candidate)
                val smoothed = RectF(
                    candidate.box.left * smoothing + detection.box.left * (1f - smoothing),
                    candidate.box.top * smoothing + detection.box.top * (1f - smoothing),
                    candidate.box.right * smoothing + detection.box.right * (1f - smoothing),
                    candidate.box.bottom * smoothing + detection.box.bottom * (1f - smoothing)
                )
                Track(candidate.id, smoothed, detection.label, 0)
            } else {
                Track(nextId++, RectF(detection.box), detection.label, 0)
            }

            currentTracks += track
            visible += TrackedDetection(
                track.id,
                detection.copy(box = RectF(track.box))
            )
        }

        tracks = (currentTracks + unmatched.map { it.copy(missedFrames = it.missedFrames + 1) })
            .filter { it.missedFrames <= maxMissedFrames }
            .toMutableList()
        return visible
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        nextId = 1L
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val areaA = maxOf(0f, a.width()) * maxOf(0f, a.height())
        val areaB = maxOf(0f, b.width()) * maxOf(0f, b.height())
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
