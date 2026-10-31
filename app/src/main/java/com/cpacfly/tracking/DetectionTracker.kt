package com.cpacfly.tracking

data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DetectionCandidate(
    val box: DetectionBox,
    val label: String,
    val score: Float,
    val categoryIndex: Int
)

data class TrackedDetection(
    val trackId: Long,
    val detection: DetectionCandidate
)

/**
 * Lightweight IoU association for consecutive detector frames.
 * Generic object tracking only; this does not recognize paper airplanes.
 */
class DetectionTracker(
    private val minimumIou: Float = 0.15f,
    private val maxMissedFrames: Int = 5,
    private val smoothing: Float = 0.65f
) {
    private data class Track(
        val id: Long,
        val box: DetectionBox,
        val label: String,
        val missedFrames: Int
    )

    private var nextId = 1L
    private var tracks = mutableListOf<Track>()

    @Synchronized
    fun update(detections: List<DetectionCandidate>): List<TrackedDetection> {
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
                val box = DetectionBox(
                    candidate.box.left * smoothing + detection.box.left * (1f - smoothing),
                    candidate.box.top * smoothing + detection.box.top * (1f - smoothing),
                    candidate.box.right * smoothing + detection.box.right * (1f - smoothing),
                    candidate.box.bottom * smoothing + detection.box.bottom * (1f - smoothing)
                )
                Track(candidate.id, box, detection.label, 0)
            } else {
                Track(nextId++, detection.box, detection.label, 0)
            }

            currentTracks += track
            visible += TrackedDetection(track.id, detection.copy(box = track.box))
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

    private fun iou(a: DetectionBox, b: DetectionBox): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        val areaA = maxOf(0f, a.right - a.left) * maxOf(0f, a.bottom - a.top)
        val areaB = maxOf(0f, b.right - b.left) * maxOf(0f, b.bottom - b.top)
        val union = areaA + areaB - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
