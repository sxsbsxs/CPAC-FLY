package com.cpacfly.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetectorOptions

/**
 * Bundled ML Kit on-device object detector in STREAM_MODE.
 * ML Kit assigns tracking IDs across successive frames. Labels are broad
 * object categories, not a paper-airplane-specific classifier.
 */
class MlKitStreamObjectDetector(context: Context) : AutoCloseable {
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
        val result = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        return result.map { item ->
            val label = item.labels.firstOrNull()
            Detection(
                box = RectF(item.boundingBox),
                label = label?.text?.takeIf(String::isNotBlank) ?: "unknown",
                score = label?.confidence ?: 0.5f,
                categoryIndex = label?.index ?: -1,
                trackingId = item.trackingId
            )
        }
    }

    override fun close() = detector.close()
}
