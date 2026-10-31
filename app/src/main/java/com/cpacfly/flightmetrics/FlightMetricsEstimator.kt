package com.cpacfly.flightmetrics

import kotlin.math.max

data class HeightSample(val timestampMs: Long, val heightMeters: Double)

enum class PredictionStatus { VALID, INSUFFICIENT_DATA, NOT_DESCENDING, INVALID_HEIGHT }

data class FlightMetrics(
    val status: PredictionStatus,
    /** Positive values mean the aircraft is descending. */
    val descentRateMetersPerSecond: Double? = null,
    val estimatedRemainingSeconds: Double? = null,
    val lowerRemainingSeconds: Double? = null,
    val upperRemainingSeconds: Double? = null,
    val fitR2: Double? = null
)

/**
 * Estimates descent from calibrated, ground-relative height samples.
 * This class does not turn image coordinates or camera distance into altitude.
 */
object FlightMetricsEstimator {
    fun estimate(input: List<HeightSample>, windowMs: Long = 5_000L): FlightMetrics {
        val samples = input
            .filter { it.timestampMs >= 0L && it.heightMeters.isFinite() && it.heightMeters >= 0.0 }
            .sortedBy { it.timestampMs }
            .distinctBy { it.timestampMs }
            .let { ordered ->
                if (ordered.isEmpty()) ordered
                else ordered.filter { it.timestampMs >= ordered.last().timestampMs - windowMs.coerceAtLeast(1L) }
            }

        if (samples.size < 3 || samples.last().timestampMs - samples.first().timestampMs < 1_000L) {
            return FlightMetrics(PredictionStatus.INSUFFICIENT_DATA)
        }

        val currentHeight = samples.last().heightMeters
        if (currentHeight <= 0.05) return FlightMetrics(PredictionStatus.INVALID_HEIGHT)

        val origin = samples.first().timestampMs
        val times = samples.map { (it.timestampMs - origin) / 1000.0 }
        val heights = samples.map { it.heightMeters }
        val meanT = times.average()
        val meanH = heights.average()
        val denominator = times.sumOf { (it - meanT) * (it - meanT) }
        if (denominator <= 1e-9) return FlightMetrics(PredictionStatus.INSUFFICIENT_DATA)

        val slope = times.indices.sumOf {
            (times[it] - meanT) * (heights[it] - meanH)
        } / denominator
        val fitted = times.indices.map { meanH + slope * (times[it] - meanT) }
        val residual = heights.indices.sumOf {
            (heights[it] - fitted[it]) * (heights[it] - fitted[it])
        }
        val total = heights.sumOf { (it - meanH) * (it - meanH) }
        val r2 = if (total <= 1e-9) 0.0 else (1.0 - residual / total).coerceIn(0.0, 1.0)
        val descentRate = -slope

        if (descentRate < 0.05 || r2 < 0.55) {
            return FlightMetrics(
                status = PredictionStatus.NOT_DESCENDING,
                descentRateMetersPerSecond = descentRate,
                fitR2 = r2
            )
        }

        val seconds = currentHeight / descentRate
        val uncertainty = max(0.25, (1.0 - r2) * 0.75)
        return FlightMetrics(
            status = PredictionStatus.VALID,
            descentRateMetersPerSecond = descentRate,
            estimatedRemainingSeconds = seconds,
            lowerRemainingSeconds = seconds * (1.0 - uncertainty).coerceAtLeast(0.1),
            upperRemainingSeconds = seconds * (1.0 + uncertainty),
            fitR2 = r2
        )
    }
}
