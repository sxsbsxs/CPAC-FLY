package com.cpacfly.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutOfViewStopControllerTest {
    @Test
    fun stopsOnlyAfterSelectedContinuousAbsenceDuration() {
        val controller = OutOfViewStopController(5_000L)
        assertFalse(controller.onFrame(true, 0L))
        assertFalse(controller.onFrame(false, 100L))
        assertFalse(controller.onFrame(false, 4_999L))
        assertTrue(controller.onFrame(false, 5_100L))
        assertFalse(controller.onFrame(false, 5_200L))
    }

    @Test
    fun targetReappearanceResetsCountdown() {
        val controller = OutOfViewStopController(5_000L)
        controller.onFrame(true, 0L)
        controller.onFrame(false, 100L)
        controller.onFrame(false, 4_000L)
        assertFalse(controller.onFrame(true, 4_100L))
        assertFalse(controller.onFrame(false, 4_200L))
        assertTrue(controller.onFrame(false, 9_200L))
    }

    @Test
    fun timeoutIsClampedToFiveSecondsThroughTwoMinutes() {
        val controller = OutOfViewStopController(1L)
        assertEquals(5_000L, controller.timeoutMs)
        controller.setTimeoutMs(500_000L)
        assertEquals(120_000L, controller.timeoutMs)
    }

    @Test
    fun ignoresNonMonotonicTimestamps() {
        val controller = OutOfViewStopController(5_000L)
        controller.onFrame(true, 1_000L)
        assertFalse(controller.onFrame(false, 900L))
        assertEquals(null, controller.missingDurationMs(2_000L))
    }
}
