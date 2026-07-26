package com.nextgen.courtvision.domain.cv

/**
 * Dribble counting via ball-motion tracking (proposal: "repeated ball-to-hand
 * proximity events" refined to bounce detection, which is more robust when the
 * hand is occluded). A bounce is a downward→upward direction reversal of the
 * ball's vertical motion while the ball is below the player's hips, with a
 * minimum travel amplitude and inter-bounce debounce window.
 */
class DribbleDetector(
    private val onDribble: (intervalMs: Long) -> Unit,
) {
    private var previousBall: BallDetection? = null
    private var descending = false
    private var descentStartY = 0f
    private var lastBounceTs = 0L

    fun reset() {
        previousBall = null
        descending = false
        lastBounceTs = 0L
    }

    fun onFrame(pose: PoseFrame?, ball: BallDetection?) {
        if (ball == null) return
        val previous = previousBall
        previousBall = ball
        if (previous == null || ball.timestampMs <= previous.timestampMs) return

        // A stale gap means tracking was lost; restart the oscillation state.
        if (ball.timestampMs - previous.timestampMs > MAX_FRAME_GAP_MS) {
            descending = false
            return
        }

        // Only count while the ball is dribble-height: below the hips.
        val hipY = hipLine(pose) ?: return
        if (ball.box.centerY < hipY) {
            descending = false
            return
        }

        val dy = ball.box.centerY - previous.box.centerY // positive = moving down
        if (!descending) {
            if (dy > MIN_STEP) {
                descending = true
                descentStartY = previous.box.centerY
            }
            return
        }

        if (dy < -MIN_STEP) {
            // Reversal: ball was falling, now rising — the bounce happened at `previous`.
            val travelled = previous.box.centerY - descentStartY
            descending = false
            if (travelled >= MIN_BOUNCE_AMPLITUDE) {
                val ts = previous.timestampMs
                if (lastBounceTs != 0L) {
                    val interval = ts - lastBounceTs
                    if (interval in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
                        onDribble(interval)
                    }
                }
                lastBounceTs = ts
            }
        }
    }

    private fun hipLine(pose: PoseFrame?): Float? {
        if (pose == null) return null
        val left = pose.landmark(PoseLandmarkIds.LEFT_HIP) ?: return null
        val right = pose.landmark(PoseLandmarkIds.RIGHT_HIP) ?: return null
        return (left.y + right.y) / 2f
    }

    private companion object {
        const val MIN_STEP = 0.004f
        const val MIN_BOUNCE_AMPLITUDE = 0.03f
        const val MIN_INTERVAL_MS = 150L
        const val MAX_INTERVAL_MS = 2_000L
        const val MAX_FRAME_GAP_MS = 500L
    }
}
