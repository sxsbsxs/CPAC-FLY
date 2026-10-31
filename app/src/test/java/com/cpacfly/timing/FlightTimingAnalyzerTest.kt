package com.cpacfly.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightTimingAnalyzerTest {
    @Test
    fun fixedCameraUsesVideoTimestampsForReleaseCandidate() {
        val analyzer = FlightTimingAnalyzer(CaptureMode.FIXED_CAMERA)
        for (i in 0 until 5) {
            analyzer.add(FrameObservation(i * 33L, 0.2, 0.4, 0.95))
        }
        analyzer.add(FrameObservation(165, 0.25, 0.4, 0.95))
        analyzer.add(FrameObservation(198, 0.31, 0.4, 0.95))
        val result = analyzer.add(FrameObservation(231, 0.37, 0.4, 0.95))
        assertEquals(TimingPhase.IN_FLIGHT, result.phase)
        assertNotNull(result.release)
        assertEquals(165L, result.release!!.mediaTimeMs)
    }

    @Test
    fun ignoresNonIncreasingVideoTimestamps() {
        val analyzer = FlightTimingAnalyzer(CaptureMode.FIXED_CAMERA)
        analyzer.add(FrameObservation(100, 0.1, 0.2, 0.9))
        val result = analyzer.add(FrameObservation(100, 0.2, 0.2, 0.9))
        assertEquals(TimingPhase.WAITING_FOR_RELEASE, result.phase)
        assertTrue(result.message.contains("忽略"))
    }

    @Test
    fun handheldModeMarksReleaseForReviewWithoutMotionCompensation() {
        val analyzer = FlightTimingAnalyzer(CaptureMode.HANDHELD_FOLLOW)
        for (i in 0 until 5) analyzer.add(FrameObservation(i * 33L, 0.2, 0.4, 0.95))
        analyzer.add(FrameObservation(165, 0.25, 0.4, 0.95))
        analyzer.add(FrameObservation(198, 0.31, 0.4, 0.95))
        val result = analyzer.add(FrameObservation(231, 0.37, 0.4, 0.95))
        assertEquals(TimingPhase.NEEDS_REVIEW, result.phase)
        assertTrue(result.release!!.requiresReview)
    }

    @Test
    fun landingRequiresGroundEvidenceAndLowMotion() {
        val analyzer = FlightTimingAnalyzer(
            CaptureMode.FIXED_CAMERA,
            landingFramesRequired = 3
        )
        for (i in 0 until 5) analyzer.add(FrameObservation(i * 33L, 0.2, 0.4, 0.95))
        analyzer.add(FrameObservation(165, 0.25, 0.4, 0.95))
        analyzer.add(FrameObservation(198, 0.31, 0.4, 0.95))
        analyzer.add(FrameObservation(231, 0.37, 0.4, 0.95))
        analyzer.add(FrameObservation(264, 0.4, 0.8, 0.95, nearGroundEvidence = true))
        analyzer.add(FrameObservation(297, 0.4, 0.8, 0.95, nearGroundEvidence = true))
        val result = analyzer.add(FrameObservation(330, 0.4, 0.8, 0.95, nearGroundEvidence = true))
        assertTrue(result.phase == TimingPhase.LANDING_CANDIDATE || result.phase == TimingPhase.COMPLETE)
    }
}
