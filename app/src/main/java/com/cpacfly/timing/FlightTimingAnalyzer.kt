package com.cpacfly.timing

import kotlin.math.hypot

enum class CaptureMode { FIXED_CAMERA, HANDHELD_FOLLOW }
enum class TimingPhase { WAITING_FOR_RELEASE, IN_FLIGHT, LANDING_CANDIDATE, COMPLETE, NEEDS_REVIEW }

data class FrameObservation(
    /** Presentation timestamp from the recorded video timeline, in milliseconds. */
    val mediaTimeMs: Long,
    /** Target center in normalized image coordinates (0..1). */
    val centerX: Double,
    val centerY: Double,
    val confidence: Double,
    /** Estimated global camera shift since the previous frame, normalized to image size. */
    val cameraShiftX: Double = 0.0,
    val cameraShiftY: Double = 0.0,
    /** Set only when a vision stage has evidence of contact with the calibrated ground region. */
    val nearGroundEvidence: Boolean = false
)

data class TimingEvent(
    val mediaTimeMs: Long,
    val confidence: Double,
    val requiresReview: Boolean
)

data class TimingAnalysis(
    val phase: TimingPhase,
    val release: TimingEvent? = null,
    val landing: TimingEvent? = null,
    val message: String
)

/**
 * Timestamp-based event candidate engine.
 *
 * FIXED_CAMERA uses target image coordinates directly.
 * HANDHELD_FOLLOW compensates target coordinates with accumulated camera motion supplied by
 * a visual-motion estimator. It deliberately refuses to claim reliable handheld timing when
 * that compensation is absent or clearly stale.
 *
 * This class only proposes events. A real app integration must provide paper-plane detections,
 * camera-motion estimates for handheld footage, and calibrated ground-contact evidence.
 */
class FlightTimingAnalyzer(
    private val mode: CaptureMode,
    private val releaseDisplacement: Double = 0.035,
    private val minimumReleaseSpeedPerSecond: Double = 0.12,
    private val landingSpeedPerSecond: Double = 0.035,
    private val releaseFramesRequired: Int = 3,
    private val landingFramesRequired: Int = 8,
    private val minimumConfidence: Double = 0.45
) {
    private data class Point(val timeMs: Long, val x: Double, val y: Double, val nearGround: Boolean)

    private val baseline = mutableListOf<Point>()
    private val recent = ArrayDeque<Point>()
    private var accumulatedCameraX = 0.0
    private var accumulatedCameraY = 0.0
    private var lastTimestamp = -1L
    private var previousPoint: Point? = null
    private var releaseRun = mutableListOf<Point>()
    private var landingRun = mutableListOf<Point>()
    private var releaseEvent: TimingEvent? = null
    private var landingEvent: TimingEvent? = null
    private var phase = TimingPhase.WAITING_FOR_RELEASE
    private var handheldCompensationSamples = 0

    @Synchronized
    fun add(observation: FrameObservation): TimingAnalysis {
        if (observation.mediaTimeMs < 0 || observation.mediaTimeMs <= lastTimestamp ||
            !observation.centerX.isFinite() || !observation.centerY.isFinite() ||
            !observation.confidence.isFinite() || observation.confidence !in 0.0..1.0
        ) return snapshot("忽略无效或非递增的视频时间戳")

        lastTimestamp = observation.mediaTimeMs
        if (observation.confidence < minimumConfidence) {
            return snapshot("目标置信度不足，等待可靠画面")
        }

        if (mode == CaptureMode.HANDHELD_FOLLOW) {
            if (observation.cameraShiftX.isFinite() && observation.cameraShiftY.isFinite() &&
                (observation.cameraShiftX != 0.0 || observation.cameraShiftY != 0.0)
            ) handheldCompensationSamples++
            accumulatedCameraX += observation.cameraShiftX
            accumulatedCameraY += observation.cameraShiftY
        }

        val x = if (mode == CaptureMode.HANDHELD_FOLLOW) observation.centerX - accumulatedCameraX else observation.centerX
        val y = if (mode == CaptureMode.HANDHELD_FOLLOW) observation.centerY - accumulatedCameraY else observation.centerY
        val point = Point(observation.mediaTimeMs, x, y, observation.nearGroundEvidence)
        val currentSpeed = speed(previousPoint, point)
        previousPoint = point

        if (phase == TimingPhase.WAITING_FOR_RELEASE) {
            if (baseline.size < 5) {
                baseline += point
                return snapshot("正在建立出手前参考位置")
            }
            val anchorX = baseline.map { it.x }.average()
            val anchorY = baseline.map { it.y }.average()
            val displacement = hypot(point.x - anchorX, point.y - anchorY)
            if (displacement >= releaseDisplacement && currentSpeed >= minimumReleaseSpeedPerSecond) {
                releaseRun += point
                if (releaseRun.size >= releaseFramesRequired) {
                    val candidateTime = releaseRun.first().timeMs
                    val compensationReady = mode == CaptureMode.FIXED_CAMERA || handheldCompensationSamples >= 3
                    releaseEvent = TimingEvent(
                        mediaTimeMs = candidateTime,
                        confidence = (0.55 + 0.1 * releaseRun.size).coerceAtMost(0.9),
                        requiresReview = !compensationReady
                    )
                    phase = if (compensationReady) TimingPhase.IN_FLIGHT else TimingPhase.NEEDS_REVIEW
                    return snapshot(
                        if (compensationReady) "检测到出手候选，请在视频时间线上复核" else
                            "手持跟拍缺少足够的相机运动补偿，出手时间需要人工复核"
                    )
                }
            } else {
                releaseRun.clear()
                if (baseline.size < 20) baseline += point else {
                    baseline.removeAt(0)
                    baseline += point
                }
            }
            return snapshot("等待纸飞机持续离手运动")
        }

        if (phase == TimingPhase.IN_FLIGHT || phase == TimingPhase.NEEDS_REVIEW || phase == TimingPhase.LANDING_CANDIDATE) {
            recent.addLast(point)
            while (recent.size > landingFramesRequired) recent.removeFirst()
            val nearGround = recent.count { it.nearGround } >= landingFramesRequired
            val lowMotion = recent.size >= landingFramesRequired && currentSpeed <= landingSpeedPerSecond
            if (nearGround && lowMotion) {
                if (phase != TimingPhase.LANDING_CANDIDATE) landingRun.clear()
                phase = TimingPhase.LANDING_CANDIDATE
                landingRun += point
                if (landingRun.size >= landingFramesRequired) {
                    landingEvent = TimingEvent(
                        mediaTimeMs = landingRun.first().timeMs,
                        confidence = 0.8,
                        requiresReview = true
                    )
                    phase = TimingPhase.COMPLETE
                    return snapshot("检测到落地候选；请逐帧复核后确认")
                }
            } else {
                landingRun.clear()
                phase = TimingPhase.IN_FLIGHT
            }
        }

        return snapshot("正在分析飞行视频时间线")
    }

    @Synchronized
    fun current(): TimingAnalysis = snapshot("等待更多视频帧")

    @Synchronized
    fun reset() {
        baseline.clear()
        recent.clear()
        releaseRun.clear()
        landingRun.clear()
        accumulatedCameraX = 0.0
        accumulatedCameraY = 0.0
        lastTimestamp = -1L
        previousPoint = null
        releaseEvent = null
        landingEvent = null
        handheldCompensationSamples = 0
        phase = TimingPhase.WAITING_FOR_RELEASE
    }

    private fun speed(previous: Point?, point: Point): Double {
        previous ?: return 0.0
        val seconds = (point.timeMs - previous.timeMs) / 1000.0
        if (seconds <= 0.0) return 0.0
        return hypot(point.x - previous.x, point.y - previous.y) / seconds
    }

    private fun snapshot(message: String) = TimingAnalysis(phase, releaseEvent, landingEvent, message)
}
