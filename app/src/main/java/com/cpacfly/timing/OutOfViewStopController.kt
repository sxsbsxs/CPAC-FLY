package com.cpacfly.timing

/**
 * Stops a flight after the selected target has remained absent from analyzed video frames.
 * Timestamps must come from the same monotonically increasing media/camera timeline.
 */
class OutOfViewStopController(
    timeoutMs: Long = 15_000L
) {
    var timeoutMs: Long = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        private set

    private var missingSinceMs: Long? = null
    private var lastTimestampMs: Long = -1L
    private var triggered = false

    @Synchronized
    fun setTimeoutMs(value: Long) {
        timeoutMs = value.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
    }

    /** Returns true once per disappearance episode when the timeout has elapsed. */
    @Synchronized
    fun onFrame(targetVisible: Boolean, timestampMs: Long): Boolean {
        if (timestampMs < 0L || timestampMs <= lastTimestampMs) return false
        lastTimestampMs = timestampMs

        if (targetVisible) {
            missingSinceMs = null
            triggered = false
            return false
        }

        if (missingSinceMs == null) missingSinceMs = timestampMs
        if (!triggered && timestampMs - missingSinceMs!! >= timeoutMs) {
            triggered = true
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        missingSinceMs = null
        lastTimestampMs = -1L
        triggered = false
    }

    @Synchronized
    fun missingDurationMs(nowMs: Long): Long? =
        missingSinceMs?.let { (nowMs - it).coerceAtLeast(0L) }

    companion object {
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 120_000L
    }
}
