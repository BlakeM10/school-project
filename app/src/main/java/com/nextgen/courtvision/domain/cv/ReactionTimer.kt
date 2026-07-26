package com.nextgen.courtvision.domain.cv

import kotlin.math.hypot

/**
 * Reaction time measurement, per the proposal: elapsed time between an audio
 * start cue and the first significant change in pose landmarks. The first pose
 * frame after arming becomes the baseline; movement is the mean displacement
 * of core landmarks (shoulders, hips, wrists) from that baseline.
 */
class ReactionTimer(
    private val onReaction: (reactionMs: Long) -> Unit,
) {
    private var cueTs: Long = NOT_ARMED
    private var baseline: PoseFrame? = null

    val isArmed: Boolean get() = cueTs != NOT_ARMED

    /** Call at the exact moment the audio cue is played. */
    fun arm(cueTimestampMs: Long) {
        cueTs = cueTimestampMs
        baseline = null
    }

    fun disarm() {
        cueTs = NOT_ARMED
        baseline = null
    }

    fun onFrame(pose: PoseFrame) {
        if (!isArmed || pose.timestampMs < cueTs) return

        val base = baseline
        if (base == null) {
            baseline = pose
            return
        }

        if (meanDisplacement(base, pose) > MOVEMENT_THRESHOLD) {
            onReaction(pose.timestampMs - cueTs)
            disarm()
        } else if (pose.timestampMs - cueTs > MAX_REACTION_MS) {
            // No response within the window — discard rather than record garbage.
            disarm()
        }
    }

    private fun meanDisplacement(a: PoseFrame, b: PoseFrame): Float {
        var total = 0f
        var count = 0
        for (id in TRACKED_LANDMARKS) {
            val la = a.landmark(id) ?: continue
            val lb = b.landmark(id) ?: continue
            total += hypot(lb.x - la.x, lb.y - la.y)
            count++
        }
        return if (count == 0) 0f else total / count
    }

    private companion object {
        const val NOT_ARMED = -1L
        const val MOVEMENT_THRESHOLD = 0.035f
        const val MAX_REACTION_MS = 3_000L
        val TRACKED_LANDMARKS = listOf(
            PoseLandmarkIds.LEFT_SHOULDER, PoseLandmarkIds.RIGHT_SHOULDER,
            PoseLandmarkIds.LEFT_HIP, PoseLandmarkIds.RIGHT_HIP,
            PoseLandmarkIds.LEFT_WRIST, PoseLandmarkIds.RIGHT_WRIST,
        )
    }
}
