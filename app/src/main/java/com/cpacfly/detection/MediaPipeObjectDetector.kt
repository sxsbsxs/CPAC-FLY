package com.cpacfly.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetectorOptions
import com.google.android.gms.tasks.Tasks

/**
 * Google ML Kit's bundled on-device object detector in STREAM_MODE.
 * ML Kit assigns tracking IDs across successive video frames. It tracks generic
 * objects; its coarse labels are not a paper-airplane classifier.
 */
class MediaPipeObjectDetector(context: Context, @Suppress("UNUSED_PARAMETER") modelFile: java.io.File? = null) : AutoCloseable {
    data class Detection(
        val box: RectF,
        val label: String,
        val score: Float,
        val categoryIndex: Int,
        val trackingId: Int? = null
    )

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    @Synchronized
    fun detect(bitmap: Bitmap, timestampMs: Long): List<Detection> {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
        val image = InputImage.fromBitmap(bitmap, 0)
        val objects = Tasks.await(detector.process(image))
        return objects.map { item ->
            val label = item.labels.firstOrNull()
            val box = item.boundingBox
            Detection(
                box = RectF(box),
                label = label?.text?.takeIf { it.isNotBlank() } ?: "unknown",
                score = label?.confidence ?: 0.5f,
                categoryIndex = label?.index ?: -1,
                trackingId = item.trackingId
            )
        }
    }

    override fun close() {
        detector.close()
    }
}
