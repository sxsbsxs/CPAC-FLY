package com.cpacfly.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalMotionEstimatorTest {
    @Test
    fun estimatesHorizontalAndVerticalTranslation() {
        val width = 120
        val height = 90
        val original = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            ((x * 73 + y * 151 + x * y * 19) xor (x * 31 + y * 7)) and 0xff
        }
        val dx = 3
        val dy = -2
        val shifted = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = x - dx
            val sourceY = y - dy
            shifted[y * width + x] =
                if (sourceX in 0 until width && sourceY in 0 until height) original[sourceY * width + sourceX] else 0
        }

        val estimator = GlobalMotionEstimator(searchRadiusPx = 6, sampleStepPx = 4)
        assertFalse(estimator.estimate(original, width, height).reliable)
        val estimate = estimator.estimate(shifted, width, height)

        assertTrue("expected reliable estimate, got $estimate", estimate.reliable)
        assertEquals(dx.toDouble() / width, estimate.shiftX, 1.0 / width)
        assertEquals(dy.toDouble() / height, estimate.shiftY, 1.0 / height)
    }

    @Test
    fun rejectsLowTextureFrames() {
        val estimator = GlobalMotionEstimator()
        val flat = IntArray(100 * 80) { 128 }
        estimator.estimate(flat, 100, 80)
        val estimate = estimator.estimate(flat, 100, 80)
        assertFalse(estimate.reliable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFrameBuffer() {
        GlobalMotionEstimator().estimate(IntArray(10), 4, 4)
    }
}
