package com.nextgen.courtvision.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Synthetic-trajectory tests for the shot state machine. Coordinates are
 * normalized image space (y grows downward). The player stands mid-frame:
 * shoulders ~0.45, wrists start ~0.6 (below shoulders), hips ~0.65.
 */
class ShotDetectorTest {

    private val events = mutableListOf<ShotEvent>()
    private lateinit var detector: ShotDetector

    @Before
    fun setUp() {
        events.clear()
        detector = ShotDetector { events += it }
    }

    private fun pose(ts: Long, wristY: Float): PoseFrame {
        val landmarks = MutableList(PoseLandmarkIds.COUNT) { Landmark(0.5f, 0.5f) }
        landmarks[PoseLandmarkIds.LEFT_SHOULDER] = Landmark(0.45f, 0.45f)
        landmarks[PoseLandmarkIds.RIGHT_SHOULDER] = Landmark(0.55f, 0.45f)
        landmarks[PoseLandmarkIds.LEFT_WRIST] = Landmark(0.48f, wristY)
        landmarks[PoseLandmarkIds.RIGHT_WRIST] = Landmark(0.52f, wristY)
        landmarks[PoseLandmarkIds.LEFT_HIP] = Landmark(0.46f, 0.65f)
        landmarks[PoseLandmarkIds.RIGHT_HIP] = Landmark(0.54f, 0.65f)
        return PoseFrame(ts, landmarks)
    }

    private fun ball(ts: Long, cx: Float, cy: Float): BallDetection =
        BallDetection(ts, NormBox(cx - 0.03f, cy - 0.03f, cx + 0.03f, cy + 0.03f), 0.9f)

    /** Drives hold → rise → release with the ball tracking the wrists. */
    private fun driveToRelease(startTs: Long): Long {
        var ts = startTs
        // Hold: ball at wrist height, wrists below shoulders
        repeat(3) {
            detector.onFrame(pose(ts, wristY = 0.60f), ball(ts, 0.5f, 0.60f), ts)
            ts += 33
        }
        // Shot motion: wrists rise above shoulders, ball follows
        detector.onFrame(pose(ts, wristY = 0.40f), ball(ts, 0.5f, 0.40f), ts)
        ts += 33
        // Release: ball separates upward away from the hands
        detector.onFrame(pose(ts, wristY = 0.42f), ball(ts, 0.5f, 0.30f), ts)
        ts += 33
        return ts
    }

    private fun flight(startTs: Long, xs: List<Float>, ys: List<Float>) {
        var ts = startTs
        for (i in xs.indices) {
            detector.onFrame(null, ball(ts, xs[i], ys[i]), ts)
            ts += 33
        }
        // Ball-lost frames to close out the flight
        detector.onFrame(null, null, ts + 700)
    }

    @Test
    fun `clean arc with straight descent is a made shot`() {
        val ts = driveToRelease(0)
        flight(
            ts,
            xs = listOf(0.52f, 0.55f, 0.58f, 0.60f, 0.61f, 0.62f, 0.63f, 0.64f),
            ys = listOf(0.24f, 0.19f, 0.16f, 0.15f, 0.17f, 0.22f, 0.28f, 0.36f),
        )
        assertEquals(1, events.size)
        assertTrue(events.first().made)
        assertTrue(events.first().releaseTimeMs > 0)
    }

    @Test
    fun `rim deflection during descent is a missed shot`() {
        val ts = driveToRelease(0)
        flight(
            ts,
            xs = listOf(0.52f, 0.55f, 0.58f, 0.60f, 0.66f, 0.57f, 0.68f, 0.55f),
            ys = listOf(0.24f, 0.19f, 0.16f, 0.15f, 0.18f, 0.24f, 0.29f, 0.37f),
        )
        assertEquals(1, events.size)
        assertFalse(events.first().made)
    }

    @Test
    fun `flat trajectory with no arc is a missed shot`() {
        val ts = driveToRelease(0)
        flight(
            ts,
            xs = listOf(0.53f, 0.56f, 0.59f, 0.62f),
            ys = listOf(0.30f, 0.30f, 0.31f, 0.33f),
        )
        assertEquals(1, events.size)
        assertFalse(events.first().made)
    }

    @Test
    fun `dribbling near the hands does not fire shot events`() {
        var ts = 0L
        // Ball oscillates low, wrists stay below shoulders
        val ys = listOf(0.70f, 0.80f, 0.90f, 0.80f, 0.70f, 0.80f, 0.90f)
        for (y in ys) {
            detector.onFrame(pose(ts, wristY = 0.62f), ball(ts, 0.5f, y), ts)
            ts += 100
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `release time measures motion start to release`() {
        var ts = 0L
        repeat(3) {
            detector.onFrame(pose(ts, wristY = 0.60f), ball(ts, 0.5f, 0.60f), ts)
            ts += 33
        }
        val motionStart = ts
        detector.onFrame(pose(ts, wristY = 0.40f), ball(ts, 0.5f, 0.40f), ts)
        ts += 500 // slow release
        detector.onFrame(pose(ts, wristY = 0.42f), ball(ts, 0.5f, 0.30f), ts)
        val releaseTs = ts

        flight(
            ts + 33,
            xs = listOf(0.55f, 0.58f, 0.60f, 0.62f, 0.64f),
            ys = listOf(0.22f, 0.17f, 0.15f, 0.20f, 0.34f),
        )
        assertEquals(1, events.size)
        assertEquals(releaseTs - motionStart, events.first().releaseTimeMs)
    }
}
