package com.nextgen.courtvision.domain.cv

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Shot detection from wrist/elbow trajectories relative to the ball bounding
 * box, per the proposal's algorithm description. A four-state machine:
 *
 *   IDLE ── ball held near a wrist ──► HOLDING
 *   HOLDING ── wrists rise above shoulders (shot motion start) ──► RISING
 *   RISING ── ball separates from hand moving upward (release) ──► FLIGHT
 *   FLIGHT ── arc tracked to apex + descent ──► classify made/missed, emit, IDLE
 *
 * Release time = shot-motion start → release (proposal definition).
 *
 * Made/missed heuristic: a made shot descends smoothly after the apex (through
 * the net), while rim hits deflect the horizontal path. We measure the maximum
 * deviation of descent points from the straight line joining the first and last
 * descent samples. This is the encapsulated, iterable part of the pipeline —
 * the ml-tools validation harness measures its precision/recall against
 * labelled clips (NFR: ≥85%), and the state machine is deliberately isolated
 * so the classifier can be tuned or replaced without touching anything else.
 */
class ShotDetector(
    private val onShot: (ShotEvent) -> Unit,
) {
    private enum class State { IDLE, HOLDING, RISING, FLIGHT }

    private var state = State.IDLE
    private var motionStartTs = 0L
    private var releaseTs = 0L
    private var releaseY = 0f
    private var lastBall: BallDetection? = null
    private val flightPath = mutableListOf<BallDetection>()

    fun reset() {
        state = State.IDLE
        flightPath.clear()
        lastBall = null
    }

    fun onFrame(pose: PoseFrame?, ball: BallDetection?, timestampMs: Long) {
        // Snapshot the previous ball BEFORE recording the current one — the
        // RISING transition compares the current position against it.
        val previousBall = lastBall
        if (ball != null && (previousBall == null || ball.timestampMs > previousBall.timestampMs)) {
            lastBall = ball
        }

        when (state) {
            State.IDLE -> {
                if (pose != null && ball != null && ballNearHand(pose, ball)) {
                    state = State.HOLDING
                }
            }

            State.HOLDING -> {
                if (pose == null || ball == null) return
                if (!ballNearHand(pose, ball)) {
                    state = State.IDLE
                    return
                }
                if (wristsAboveShoulders(pose)) {
                    motionStartTs = timestampMs
                    state = State.RISING
                }
            }

            State.RISING -> {
                if (ball == null) return
                val previous = previousBall?.takeIf { it.timestampMs < ball.timestampMs } ?: return
                val movingUp = ball.box.centerY < previous.box.centerY - MIN_UPWARD_STEP
                val separated = pose == null || !ballNearHand(pose, ball)
                if (separated && movingUp) {
                    releaseTs = timestampMs
                    releaseY = ball.box.centerY
                    flightPath.clear()
                    flightPath += ball
                    state = State.FLIGHT
                } else if (pose != null && !wristsAboveShoulders(pose) && !ballNearHand(pose, ball)) {
                    // Motion aborted (e.g. pass or dribble instead of a shot)
                    state = State.IDLE
                }
            }

            State.FLIGHT -> {
                if (ball != null) flightPath += ball
                val last = flightPath.lastOrNull() ?: return
                val ballLost = timestampMs - last.timestampMs > BALL_LOST_TIMEOUT_MS
                val fellBelowRelease = last.box.centerY > releaseY + DESCENT_END_MARGIN
                val timedOut = timestampMs - releaseTs > MAX_FLIGHT_MS
                if (ballLost || fellBelowRelease || timedOut) {
                    finishFlight()
                }
            }
        }
    }

    private fun finishFlight() {
        val made = classifyFlight()
        onShot(
            ShotEvent(
                timestampMs = releaseTs,
                made = made,
                releaseTimeMs = max(0L, releaseTs - motionStartTs),
            ),
        )
        state = State.IDLE
        flightPath.clear()
    }

    private fun classifyFlight(): Boolean {
        // A real shot must actually arc upward from the release point.
        val apexY = flightPath.minOfOrNull { it.box.centerY } ?: return false
        if (releaseY - apexY < MIN_ARC_HEIGHT) return false

        val apexIndex = flightPath.indexOfFirst { it.box.centerY == apexY }
        val descent = flightPath.drop(apexIndex)
        // Too little descent evidence to confirm a clean drop — conservative miss.
        if (descent.size < MIN_DESCENT_SAMPLES) return false

        val first = descent.first().box
        val last = descent.last().box
        val pathLength = hypot(last.centerX - first.centerX, last.centerY - first.centerY)
        if (pathLength <= 0f) return false

        // Maximum perpendicular deviation of descent samples from the straight
        // first→last chord; rim deflections bend the path.
        var maxDeviation = 0f
        for (sample in descent) {
            val deviation = pointToLineDistance(
                sample.box.centerX, sample.box.centerY,
                first.centerX, first.centerY,
                last.centerX, last.centerY,
            )
            maxDeviation = max(maxDeviation, deviation)
        }
        return maxDeviation < MAX_DESCENT_DEVIATION
    }

    private fun ballNearHand(pose: PoseFrame, ball: BallDetection): Boolean {
        val threshold = max(ball.box.width, ball.box.height) * PROXIMITY_FACTOR
        return listOf(PoseLandmarkIds.LEFT_WRIST, PoseLandmarkIds.RIGHT_WRIST).any { id ->
            val wrist = pose.landmark(id) ?: return@any false
            hypot(wrist.x - ball.box.centerX, wrist.y - ball.box.centerY) < threshold
        }
    }

    private fun wristsAboveShoulders(pose: PoseFrame): Boolean {
        val shoulderY = minOf(
            pose.landmark(PoseLandmarkIds.LEFT_SHOULDER)?.y ?: return false,
            pose.landmark(PoseLandmarkIds.RIGHT_SHOULDER)?.y ?: return false,
        )
        val wristY = min(
            pose.landmark(PoseLandmarkIds.LEFT_WRIST)?.y ?: return false,
            pose.landmark(PoseLandmarkIds.RIGHT_WRIST)?.y ?: return false,
        )
        return wristY < shoulderY // y grows downward: above = smaller
    }

    private fun pointToLineDistance(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return hypot(px - x1, py - y1)
        return abs(dy * px - dx * py + x2 * y1 - y2 * x1) / hypot(dx, dy)
    }

    private companion object {
        const val PROXIMITY_FACTOR = 1.4f
        const val MIN_UPWARD_STEP = 0.002f
        const val MIN_ARC_HEIGHT = 0.05f
        const val MIN_DESCENT_SAMPLES = 3
        const val MAX_DESCENT_DEVIATION = 0.035f
        const val DESCENT_END_MARGIN = 0.02f
        const val BALL_LOST_TIMEOUT_MS = 600L
        const val MAX_FLIGHT_MS = 3_000L
    }
}
