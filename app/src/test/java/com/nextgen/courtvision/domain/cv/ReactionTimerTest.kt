package com.nextgen.courtvision.domain.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReactionTimerTest {

    private val reactions = mutableListOf<Long>()
    private lateinit var timer: ReactionTimer

    @Before
    fun setUp() {
        reactions.clear()
        timer = ReactionTimer { reactions += it }
    }

    private fun pose(ts: Long, offsetX: Float = 0f): PoseFrame {
        val landmarks = MutableList(PoseLandmarkIds.COUNT) { Landmark(0.5f + offsetX, 0.5f) }
        return PoseFrame(ts, landmarks)
    }

    @Test
    fun `measures time from cue to first significant movement`() {
        timer.arm(cueTimestampMs = 1_000)
        timer.onFrame(pose(1_050)) // baseline
        timer.onFrame(pose(1_150)) // still
        timer.onFrame(pose(1_350, offsetX = 0.10f)) // moved!

        assertEquals(listOf(350L), reactions)
        assertFalse(timer.isArmed)
    }

    @Test
    fun `small drift below threshold does not trigger`() {
        timer.arm(1_000)
        timer.onFrame(pose(1_050))
        timer.onFrame(pose(1_150, offsetX = 0.01f))
        timer.onFrame(pose(1_250, offsetX = 0.02f))

        assertTrue(reactions.isEmpty())
        assertTrue(timer.isArmed)
    }

    @Test
    fun `frames before the cue are ignored`() {
        timer.arm(2_000)
        timer.onFrame(pose(1_500, offsetX = 0.2f)) // pre-cue movement
        timer.onFrame(pose(2_050)) // baseline
        timer.onFrame(pose(2_450, offsetX = 0.1f))

        assertEquals(listOf(450L), reactions)
    }

    @Test
    fun `no response within the window disarms without emitting`() {
        timer.arm(1_000)
        timer.onFrame(pose(1_050))
        timer.onFrame(pose(4_500)) // way past the 3s window, still still

        assertTrue(reactions.isEmpty())
        assertFalse(timer.isArmed)
    }

    @Test
    fun `not armed means no measurements`() {
        timer.onFrame(pose(1_000))
        timer.onFrame(pose(1_100, offsetX = 0.3f))
        assertTrue(reactions.isEmpty())
    }

    @Test
    fun `re-arming measures a second rep`() {
        timer.arm(1_000)
        timer.onFrame(pose(1_050))
        timer.onFrame(pose(1_300, offsetX = 0.1f))

        timer.arm(5_000)
        timer.onFrame(pose(5_040))
        timer.onFrame(pose(5_640, offsetX = 0.1f))

        assertEquals(listOf(300L, 640L), reactions)
    }
}
