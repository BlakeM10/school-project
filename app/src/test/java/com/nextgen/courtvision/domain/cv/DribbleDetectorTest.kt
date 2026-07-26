package com.nextgen.courtvision.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DribbleDetectorTest {

    private val intervals = mutableListOf<Long>()
    private lateinit var detector: DribbleDetector

    @Before
    fun setUp() {
        intervals.clear()
        detector = DribbleDetector { intervals += it }
    }

    private fun pose(ts: Long): PoseFrame {
        val landmarks = MutableList(PoseLandmarkIds.COUNT) { Landmark(0.5f, 0.4f) }
        landmarks[PoseLandmarkIds.LEFT_HIP] = Landmark(0.46f, 0.55f)
        landmarks[PoseLandmarkIds.RIGHT_HIP] = Landmark(0.54f, 0.55f)
        return PoseFrame(ts, landmarks)
    }

    private fun ball(ts: Long, cy: Float): BallDetection =
        BallDetection(ts, NormBox(0.47f, cy - 0.03f, 0.53f, cy + 0.03f), 0.9f)

    /** Feeds a bouncing ball: fall to the floor and rise, repeatedly. */
    private fun bounce(startTs: Long, periodMs: Long, cycles: Int): Long {
        var ts = startTs
        repeat(cycles) {
            for (y in listOf(0.70f, 0.78f, 0.86f, 0.94f)) { // falling
                detector.onFrame(pose(ts), ball(ts, y))
                ts += periodMs / 8
            }
            for (y in listOf(0.86f, 0.78f, 0.70f, 0.62f)) { // rising
                detector.onFrame(pose(ts), ball(ts, y))
                ts += periodMs / 8
            }
        }
        return ts
    }

    @Test
    fun `counts one interval per bounce cycle after the first bounce`() {
        bounce(startTs = 0, periodMs = 400, cycles = 4)
        // 4 bounces → 3 inter-bounce intervals
        assertEquals(3, intervals.size)
    }

    @Test
    fun `intervals reflect the bounce period`() {
        bounce(startTs = 0, periodMs = 400, cycles = 3)
        assertTrue(intervals.all { it in 300L..500L })
    }

    @Test
    fun `ball above the hips is not a dribble`() {
        var ts = 0L
        for (y in listOf(0.30f, 0.38f, 0.46f, 0.38f, 0.30f, 0.38f, 0.46f, 0.38f)) {
            detector.onFrame(pose(ts), ball(ts, y))
            ts += 60
        }
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun `tiny jitter below amplitude threshold is ignored`() {
        var ts = 0L
        for (y in listOf(0.80f, 0.81f, 0.80f, 0.81f, 0.80f, 0.81f, 0.80f)) {
            detector.onFrame(pose(ts), ball(ts, y))
            ts += 60
        }
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun `tracking gap resets without emitting a bogus interval`() {
        val end = bounce(startTs = 0, periodMs = 400, cycles = 2) // 1 interval
        // Long gap (tracking lost), then two more bounces → gap interval invalid,
        // but the pair after the gap yields exactly one more.
        bounce(startTs = end + 5_000, periodMs = 400, cycles = 2)
        assertEquals(2, intervals.size)
    }

    @Test
    fun `without pose frames nothing is counted`() {
        var ts = 0L
        for (y in listOf(0.70f, 0.80f, 0.90f, 0.80f, 0.70f)) {
            detector.onFrame(null, ball(ts, y))
            ts += 60
        }
        assertTrue(intervals.isEmpty())
    }
}
