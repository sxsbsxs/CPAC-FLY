package com.cpacfly.vision

import kotlin.math.abs

data class CameraMotionEstimate(
    /** Translation of the scene between frames, in normalized image coordinates. */
    val shiftX: Double,
    val shiftY: Double,
    val confidence: Double,
    val reliable: Boolean
)

/**
 * Translation-only global motion estimator for grayscale preview frames.
 *
 * This is a lightweight block-matching baseline, not a full optical-flow solution. It is useful
 * for fixed focal length handheld panning with limited rotation/parallax. Callers must reject
 * low-confidence estimates rather than feeding them into flight timing as ground truth.
 */
class GlobalMotionEstimator(
    private val searchRadiusPx: Int = 10,
    private val sampleStepPx: Int = 6,
    private val minimumSamples: Int = 40,
    private val maximumMeanError: Double = 24.0,
    private val minimumSeparation: Double = 0.08
) {
    private var previous: IntArray? = null
    private var previousWidth = 0
    private var previousHeight = 0

    @Synchronized
    fun estimate(current: IntArray, width: Int, height: Int): CameraMotionEstimate {
        require(width > 0 && height > 0 && current.size == width * height) {
            "Frame dimensions do not match grayscale pixel buffer"
        }
        val old = previous
        val oldWidth = previousWidth
        val oldHeight = previousHeight
        previous = current.copyOf()
        previousWidth = width
        previousHeight = height

        if (old == null || oldWidth != width || oldHeight != height) {
            return CameraMotionEstimate(0.0, 0.0, 0.0, false)
        }

        var meanOld = 0.0
        var meanCurrent = 0.0
        var count = 0
        for (y in searchRadiusPx until height - searchRadiusPx step sampleStepPx) {
            for (x in searchRadiusPx until width - searchRadiusPx step sampleStepPx) {
                meanOld += old[y * width + x]
                meanCurrent += current[y * width + x]
                count++
            }
        }
        if (count < minimumSamples) return CameraMotionEstimate(0.0, 0.0, 0.0, false)
        meanOld /= count
        meanCurrent /= count

        var variance = 0.0
        for (y in searchRadiusPx until height - searchRadiusPx step sampleStepPx) {
            for (x in searchRadiusPx until width - searchRadiusPx step sampleStepPx) {
                val delta = old[y * width + x] - meanOld
                variance += delta * delta
            }
        }
        if (variance / count < 18.0) return CameraMotionEstimate(0.0, 0.0, 0.0, false)

        var bestError = Double.POSITIVE_INFINITY
        var secondError = Double.POSITIVE_INFINITY
        var bestDx = 0
        var bestDy = 0
        for (dy in -searchRadiusPx..searchRadiusPx) {
            for (dx in -searchRadiusPx..searchRadiusPx) {
                var error = 0.0
                var samples = 0
                for (y in searchRadiusPx until height - searchRadiusPx step sampleStepPx) {
                    val currentY = y + dy
                    for (x in searchRadiusPx until width - searchRadiusPx step sampleStepPx) {
                        val currentX = x + dx
                        val a = old[y * width + x] - meanOld
                        val b = current[currentY * width + currentX] - meanCurrent
                        error += abs(a - b)
                        samples++
                    }
                }
                val averageError = error / samples
                if (averageError < bestError) {
                    secondError = bestError
                    bestError = averageError
                    bestDx = dx
                    bestDy = dy
                } else if (averageError < secondError) {
                    secondError = averageError
                }
            }
        }

        val separation = if (secondError.isFinite() && secondError > 0.0) {
            ((secondError - bestError) / secondError).coerceIn(0.0, 1.0)
        } else 0.0
        val quality = (1.0 - bestError / maximumMeanError).coerceIn(0.0, 1.0)
        val confidence = quality * (0.5 + separation * 0.5)
        val reliable = bestError <= maximumMeanError && separation >= minimumSeparation
        return CameraMotionEstimate(
            shiftX = bestDx.toDouble() / width,
            shiftY = bestDy.toDouble() / height,
            confidence = confidence,
            reliable = reliable
        )
    }

    @Synchronized
    fun reset() {
        previous = null
        previousWidth = 0
        previousHeight = 0
    }
}
