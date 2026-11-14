package com.devsquid.convertercamera.business

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Combines price detections from several captures of the same scene so that a sporadic misread
 * (e.g. "￥74" read as "474" in one frame) is outvoted by the other frames.
 *
 * Frame 0 is the reference: its image is the one shown to the user, and every returned box is in
 * its coordinates. Later frames are shifted onto it by the median offset between detections of
 * the same value, which compensates for small hand movement between captures.
 *
 * - Detections are grouped across frames by position (at most one detection per frame per group).
 * - Each group's value is the one read most often; ties go to the earliest frame.
 * - Groups present in the reference frame are always kept, so the result is never sparser than a
 *   single capture. Groups the reference frame missed are kept only with [minVotes] frames.
 */
class FrameVoter(private val minVotes: Int) {

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
        fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
    }

    data class Detection<T>(val box: Box, val value: BigDecimal, val payload: T)

    data class Voted<T>(val box: Box, val payload: T, val votes: Int)

    private class Member<T>(val frame: Int, val detection: Detection<T>)

    private class Cluster<T>(first: Member<T>) {
        val members = mutableListOf(first)
        val box get() = members.first().detection.box
        val value get() = members.first().detection.value
        val lastFrame get() = members.last().frame
    }

    fun <T> vote(frames: List<List<Detection<T>>>): List<Voted<T>> {
        if (frames.isEmpty()) return emptyList()
        val reference = frames.first()
        val aligned = frames.mapIndexed { i, frame -> if (i == 0) frame else frame.alignTo(reference) }

        val clusters = mutableListOf<Cluster<T>>()
        aligned.forEachIndexed { frameIndex, frame ->
            // Assign this frame's detections to existing clusters by lowest cost first, so one
            // detection can't take a cluster that another detection in this frame fits better.
            val candidates = frame.flatMap { d ->
                clusters
                    .filter { it.lastFrame != frameIndex }
                    .mapNotNull { c ->
                        val dist = normalizedDistance(c.box, d.box)
                        if (dist >= MAX_MATCH_DISTANCE) return@mapNotNull null
                        val penalty = if (sameValue(c.value, d.value)) 0f else VALUE_MISMATCH_PENALTY
                        Triple(d, c, dist + penalty)
                    }
            }.sortedBy { it.third }

            val usedDetections = HashSet<Detection<T>>()
            val usedClusters = HashSet<Cluster<T>>()
            candidates.forEach { (d, c, _) ->
                if (d in usedDetections || c in usedClusters) return@forEach
                c.members += Member(frameIndex, d)
                usedDetections += d
                usedClusters += c
            }
            frame.filter { it !in usedDetections }.forEach { clusters += Cluster(Member(frameIndex, it)) }
        }

        return clusters.mapNotNull { cluster ->
            val inReference = cluster.members.first().frame == 0
            // groupBy keeps insertion (frame) order, and maxBy returns the first maximum,
            // so ties resolve to the value seen in the earliest frame.
            val (_, winners) = cluster.members
                .groupBy { it.detection.value.stripTrailingZeros() }
                .entries
                .maxBy { it.value.size }
            if (!inReference && winners.size < minVotes) return@mapNotNull null
            Voted(box = cluster.box, payload = winners.first().detection.payload, votes = winners.size)
        }
    }

    /** Shifts this frame onto [reference] by the median offset between same-value matches. */
    private fun <T> List<Detection<T>>.alignTo(reference: List<Detection<T>>): List<Detection<T>> {
        val offsets = mapNotNull { d ->
            reference
                .filter { sameValue(it.value, d.value) }
                .minByOrNull { normalizedDistance(it.box, d.box) }
                ?.takeIf { normalizedDistance(it.box, d.box) < MAX_ALIGN_DISTANCE }
                ?.let { (it.box.centerX - d.box.centerX) to (it.box.centerY - d.box.centerY) }
        }
        if (offsets.isEmpty()) return this
        val dx = offsets.map { it.first }.sorted()[offsets.size / 2]
        val dy = offsets.map { it.second }.sorted()[offsets.size / 2]
        return map { it.copy(box = it.box.offset(dx, dy)) }
    }

    companion object {
        /** Max centre distance, in box widths + box heights, for two detections to match. */
        private const val MAX_MATCH_DISTANCE = 1f

        /** Looser limit used only to estimate the frame-to-frame shift before matching. */
        private const val MAX_ALIGN_DISTANCE = 2f

        /** Prefers same-value matches when boxes overlap (e.g. ML Kit uses the line box). */
        private const val VALUE_MISMATCH_PENALTY = 0.5f

        private fun sameValue(a: BigDecimal, b: BigDecimal) = a.compareTo(b) == 0

        private fun normalizedDistance(a: Box, b: Box): Float {
            val w = maxOf(a.width, b.width, 1f)
            val h = maxOf(a.height, b.height, 1f)
            return abs(a.centerX - b.centerX) / w + abs(a.centerY - b.centerY) / h
        }
    }
}
