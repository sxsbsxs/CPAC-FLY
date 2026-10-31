package com.cpacfly.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import java.io.File
import java.nio.ByteBuffer
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Local, on-device object detector backed by Google's pretrained
 * MediaPipe EfficientDet-Lite0 COCO model.
 *
 * Important: the COCO model is a generic 80-class detector. It was not trained
 * specifically for paper airplanes, so "airplane" detections are only a
 * prototype signal and must not be presented as reliable paper-plane recognition.
 */
class MediaPipeObjectDetector(context: Context, modelFile: File) : AutoCloseable {

    data class Detection(
        val box: RectF,
        val label: String,
        val score: Float,
        val categoryIndex: Int
    )

    private val detector: ObjectDetector

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetBuffer(ByteBuffer.wrap(modelFile.readBytes()))
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .build()

        detector = ObjectDetector.createFromOptions(context, options)
    }

    /**
     * Run inference on a decoded camera frame.
     * timestampMs must be monotonic and derived from the camera capture timestamp,
     * not from UI time or inference completion time.
     */
    @Synchronized
    fun detect(bitmap: Bitmap, timestampMs: Long): List<Detection> {
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }

        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = detector.detectForVideo(mpImage, timestampMs)

        return result.detections().mapNotNull { detection ->
            val category = detection.categories().maxByOrNull { it.score() }
                ?: return@mapNotNull null
            val bounds = detection.boundingBox()
            Detection(
                box = RectF(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat()
                ),
                label = category.categoryName(),
                score = category.score(),
                categoryIndex = category.index()
            )
        }
    }

    override fun close() {
        detector.close()
    }

    companion object {
        private const val MAX_RESULTS = 10
        private const val SCORE_THRESHOLD = 0.30f
    }
}
