package com.cpacfly.video

/**
 * Pure helpers for seeking a decoded video by presentation timestamps.
 * Input timestamps should be sorted and unique; [normalize] provides that invariant.
 */
object VideoFrameTimeline {
    fun normalize(timestampsMs: List<Long>): List<Long> =
        timestampsMs.asSequence().filter { it >= 0L }.distinct().sorted().toList()

    fun nearestFrameIndex(timestampsMs: List<Long>, targetMs: Long): Int {
        if (timestampsMs.isEmpty()) return -1
        val found = timestampsMs.binarySearch(targetMs)
        if (found >= 0) return found
        val insertion = -found - 1
        if (insertion <= 0) return 0
        if (insertion >= timestampsMs.size) return timestampsMs.lastIndex
        val before = timestampsMs[insertion - 1]
        val after = timestampsMs[insertion]
        return if (targetMs - before <= after - targetMs) insertion - 1 else insertion
    }
}
