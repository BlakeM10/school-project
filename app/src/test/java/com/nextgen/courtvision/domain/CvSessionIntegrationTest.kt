package com.nextgen.courtvision.domain

import com.nextgen.courtvision.domain.cv.BallDetection
import com.nextgen.courtvision.domain.cv.DribbleDetector
import com.nextgen.courtvision.domain.cv.Landmark
import com.nextgen.courtvision.domain.cv.NormBox
import com.nextgen.courtvision.domain.cv.PoseFrame
import com.nextgen.courtvision.domain.cv.PoseLandmarkIds
import com.nextgen.courtvision.domain.cv.ShotDetector
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure
import com.nextgen.courtvision.domain.session.SessionRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM integration test per the proposal's integration level: a scripted
 * "pre-recorded clip" (synthetic pose + ball frame sequence) flows through the
 * detection algorithms into SessionRecorder exactly as CameraCVPipeline wires
 * them, and the resulting Session document is checked end-to-end.
 */
class CvSessionIntegrationTest {

    private val drill = Drill(
        id = "combo_dribble_shoot",
        name = "Combo",
        category = DrillCategory.COMBINED,
        instructions = "",
        targetMetrics = emptyMap(),
        durationSec = 420,
        measures = listOf(Measure.SHOTS, Measure.RELEASE_TIME, Measure.DRIBBLES),
        sortOrder = 8,
    )

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

    @Test
    fun `scripted clip produces a coherent session document`() {
        var now = 1_000_000L
        val clock = { now }
        val recorder = SessionRecorder("uid-1", "team-1", drill, "0.1.0", clock)

        // Wire detectors to the recorder exactly as CameraCVPipeline does.
        val shotDetector = ShotDetector { event ->
            recorder.recordShot(event.made, event.releaseTimeMs)
        }
        val dribbleDetector = DribbleDetector { interval ->
            recorder.recordDribble(interval)
        }

        recorder.start()

        // --- Scene 1: four dribbles below the hips ---
        var ts = now
        repeat(5) { cycle ->
            for (y in listOf(0.70f, 0.78f, 0.86f, 0.94f, 0.86f, 0.78f, 0.70f, 0.62f)) {
                val frame = pose(ts, wristY = 0.62f)
                val b = ball(ts, 0.5f, y)
                dribbleDetector.onFrame(frame, b)
                shotDetector.onFrame(frame, b, ts)
                ts += 50
            }
        }

        // --- Scene 2: a made jump shot ---
        repeat(3) {
            val frame = pose(ts, wristY = 0.60f)
            val b = ball(ts, 0.5f, 0.60f)
            shotDetector.onFrame(frame, b, ts)
            dribbleDetector.onFrame(frame, b)
            ts += 33
        }
        shotDetector.onFrame(pose(ts, wristY = 0.40f), ball(ts, 0.5f, 0.40f), ts)
        ts += 200
        shotDetector.onFrame(pose(ts, wristY = 0.42f), ball(ts, 0.5f, 0.30f), ts)
        ts += 33
        val flightXs = listOf(0.52f, 0.55f, 0.58f, 0.60f, 0.61f, 0.62f, 0.63f, 0.64f)
        val flightYs = listOf(0.24f, 0.19f, 0.16f, 0.15f, 0.17f, 0.22f, 0.28f, 0.36f)
        for (i in flightXs.indices) {
            shotDetector.onFrame(null, ball(ts, flightXs[i], flightYs[i]), ts)
            ts += 33
        }
        shotDetector.onFrame(null, null, ts + 700)

        // --- Finish the session ---
        now = ts + 1_000
        val session = recorder.finish()

        assertEquals(1, session.shotsAttempted)
        assertEquals(1, session.shotsMade)
        assertEquals(100.0, session.accuracyPct, 0.001)
        assertEquals(200L, session.releaseTimesMs.single())
        // 5 bounce cycles → 4 inter-bounce intervals
        assertEquals(4, session.dribbleCount)
        assertTrue(
            "dribble speed ${session.dribbleSpeedHz} should be ~2.5Hz",
            session.dribbleSpeedHz in 2.0..3.0,
        )
        assertEquals("uid-1", session.playerId)
        assertEquals(drill.id, session.drillId)
        assertTrue(session.durationSec > 0)
    }
}
