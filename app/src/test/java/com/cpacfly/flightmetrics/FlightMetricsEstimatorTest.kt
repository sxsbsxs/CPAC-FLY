package com.cpacfly.flightmetrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightMetricsEstimatorTest {
    @Test
    fun estimatesStableDescentAndRemainingTime() {
        val samples = listOf(
            HeightSample(0, 5.0), HeightSample(1_000, 4.5),
            HeightSample(2_000, 4.0), HeightSample(3_000, 3.5),
            HeightSample(4_000, 3.0)
        )
        val result = FlightMetricsEstimator.estimate(samples)
        assertEquals(PredictionStatus.VALID, result.status)
        assertEquals(0.5, result.descentRateMetersPerSecond!!, 0.001)
        assertEquals(6.0, result.estimatedRemainingSeconds!!, 0.001)
        assertNotNull(result.lowerRemainingSeconds)
        assertNotNull(result.upperRemainingSeconds)
    }

    @Test
    fun doesNotPredictWhenAircraftIsRising() {
        val result = FlightMetricsEstimator.estimate(
            listOf(
                HeightSample(0, 1.0), HeightSample(1_000, 1.2),
                HeightSample(2_000, 1.4), HeightSample(3_000, 1.6)
            )
        )
        assertEquals(PredictionStatus.NOT_DESCENDING, result.status)
        assertNull(result.estimatedRemainingSeconds)
    }

    @Test
    fun requiresEnoughTimestampedSamples() {
        val result = FlightMetricsEstimator.estimate(
            listOf(HeightSample(0, 1.0), HeightSample(500, 0.8))
        )
        assertEquals(PredictionStatus.INSUFFICIENT_DATA, result.status)
        assertTrue(result.descentRateMetersPerSecond == null)
    }
}
