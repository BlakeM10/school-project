package com.nextgen.courtvision.domain.session

import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    private val drill = Drill(
        id = "free_throw_series",
        name = "Free Throw Series",
        category = DrillCategory.SHOOTING,
        instructions = "",
        targetMetrics = emptyMap(),
        durationSec = 300,
        measures = listOf(Measure.SHOTS, Measure.RELEASE_TIME),
        sortOrder = 1,
    )

    private class FakeClock(var now: Long = 1_000_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun recorder(clock: FakeClock) = SessionRecorder(
        playerId = "uid-1",
        teamId = "team-1",
        drill = drill,
        appVersion = "0.1.0",
        clock = { clock.now },
    )

    @Test
    fun `full session lifecycle produces correct metrics`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()

        recorder.recordShot(made = true, releaseTimeMs = 700)
        recorder.recordShot(made = false, releaseTimeMs = 500)
        recorder.recordShot(made = true, releaseTimeMs = 600)
        recorder.recordShot(made = true, releaseTimeMs = 0) // release not detected
        clock.advance(120_000)

        val session = recorder.finish()

        assertEquals("uid-1", session.playerId)
        assertEquals("team-1", session.teamId)
        assertEquals("free_throw_series", session.drillId)
        assertEquals(120, session.durationSec)
        assertEquals(4, session.shotsAttempted)
        assertEquals(3, session.shotsMade)
        assertEquals(75.0, session.accuracyPct, 0.001)
        assertEquals(listOf(700L, 500L, 600L), session.releaseTimesMs)
        assertEquals(600.0, session.avgReleaseTimeMs, 0.001)
        assertEquals("0.1.0", session.appVersion)
    }

    @Test
    fun `dribble speed derives from mean inter-bounce interval`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()

        // 400ms mean interval → 2.5 bounces per second
        recorder.recordDribble(300)
        recorder.recordDribble(500)
        recorder.recordDribble(400)

        val session = recorder.finish()
        assertEquals(3, session.dribbleCount)
        assertEquals(2.5, session.dribbleSpeedHz, 0.001)
    }

    @Test
    fun `reaction times average into derived metric`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()

        recorder.recordReaction(350)
        recorder.recordReaction(450)

        val session = recorder.finish()
        assertEquals(listOf(350L, 450L), session.reactionTimesMs)
        assertEquals(400.0, session.avgReactionTimeMs, 0.001)
    }

    @Test
    fun `empty session finishes with zeroed metrics`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        clock.advance(5_000)

        val session = recorder.finish()
        assertEquals(0, session.shotsAttempted)
        assertEquals(0.0, session.accuracyPct, 0.0)
        assertEquals(0.0, session.dribbleSpeedHz, 0.0)
        assertEquals(5, session.durationSec)
    }

    @Test
    fun `live stats reflect events and elapsed time`() {
        val clock = FakeClock()
        val recorder = recorder(clock)
        recorder.start()
        recorder.recordShot(made = true, releaseTimeMs = 650)
        recorder.recordDribble(400)
        clock.advance(65_000)

        val stats = recorder.currentStats()
        assertEquals(1, stats.shotsAttempted)
        assertEquals(1, stats.shotsMade)
        assertEquals(1, stats.dribbleCount)
        assertEquals(65, stats.elapsedSec)
    }

    @Test
    fun `events before start or after finish are rejected`() {
        val clock = FakeClock()
        val recorder = recorder(clock)

        assertThrows(IllegalStateException::class.java) {
            recorder.recordShot(made = true, releaseTimeMs = 100)
        }

        recorder.start()
        assertTrue(recorder.isRunning)
        recorder.finish()
        assertFalse(recorder.isRunning)

        assertThrows(IllegalStateException::class.java) {
            recorder.recordDribble(400)
        }
        assertThrows(IllegalStateException::class.java) { recorder.finish() }
    }

    @Test
    fun `double start is rejected`() {
        val recorder = recorder(FakeClock())
        recorder.start()
        assertThrows(IllegalStateException::class.java) { recorder.start() }
    }
}
