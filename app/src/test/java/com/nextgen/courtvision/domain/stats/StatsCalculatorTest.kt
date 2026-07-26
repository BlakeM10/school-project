package com.nextgen.courtvision.domain.stats

import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val day = 86_400_000L
    private val now = 100L * day + 5_000_000L // arbitrary fixed "today"

    private fun session(
        daysAgo: Long,
        made: Int,
        attempted: Int,
        id: String = "s$daysAgo-$made",
    ) = Session(
        id = id,
        playerId = "p1",
        teamId = "t1",
        drillId = "free_throw_series",
        startedAtMillis = now - daysAgo * day,
        durationSec = 300,
        shotsAttempted = attempted,
        shotsMade = made,
        accuracyPct = if (attempted > 0) made * 100.0 / attempted else 0.0,
    )

    @Test
    fun `empty history yields the empty stats object`() {
        assertEquals(PlayerStats.EMPTY, StatsCalculator.compute(emptyList(), now))
    }

    @Test
    fun `aggregates shots accuracy and personal best`() {
        val sessions = listOf(
            session(3, made = 5, attempted = 10),  // 50%
            session(2, made = 7, attempted = 10),  // 70%
            session(1, made = 6, attempted = 10),  // 60%
        )
        val stats = StatsCalculator.compute(sessions, now)
        assertEquals(3, stats.sessionCount)
        assertEquals(30, stats.totalShots)
        assertEquals(18, stats.totalMade)
        assertEquals(60.0, stats.avgAccuracyPct, 0.001)
        assertEquals(70.0, stats.bestAccuracyPct, 0.001)
        assertEquals(70.0, stats.personalBest!!.accuracyPct, 0.001)
    }

    @Test
    fun `streak counts consecutive days and survives no-session-today`() {
        val active = StatsCalculator.compute(
            listOf(session(0, 5, 10), session(1, 5, 10), session(2, 5, 10)),
            now,
        )
        assertEquals(3, active.streakDays)
        assertTrue(active.trainedToday)

        // Trained yesterday + day before, not yet today: streak intact at 2.
        val pending = StatsCalculator.compute(
            listOf(session(1, 5, 10), session(2, 5, 10)),
            now,
        )
        assertEquals(2, pending.streakDays)
        assertFalse(pending.trainedToday)

        // Gap two days ago breaks it.
        val broken = StatsCalculator.compute(
            listOf(session(2, 5, 10), session(3, 5, 10)),
            now,
        )
        assertEquals(0, broken.streakDays)
    }

    @Test
    fun `accuracy delta compares recent block to previous block`() {
        val sessions =
            (10L downTo 6L).map { session(it, made = 4, attempted = 10) } + // 40% x5
                (5L downTo 1L).map { session(it, made = 6, attempted = 10) } // 60% x5
        val stats = StatsCalculator.compute(sessions, now)
        assertEquals(20.0, stats.accuracyDeltaPct, 0.001)
    }

    @Test
    fun `period filter keeps only sessions inside the window`() {
        val sessions = listOf(session(1, 5, 10), session(20, 5, 10), session(200, 5, 10))
        assertEquals(1, StatsCalculator.filterByPeriod(sessions, StatsPeriod.WEEK, now).size)
        assertEquals(2, StatsCalculator.filterByPeriod(sessions, StatsPeriod.MONTH, now).size)
        assertEquals(3, StatsCalculator.filterByPeriod(sessions, StatsPeriod.YEAR, now).size)
        assertEquals(3, StatsCalculator.filterByPeriod(sessions, StatsPeriod.ALL, now).size)
    }

    @Test
    fun `player summaries rank by average accuracy and flag trends`() {
        val alice = Player("a", "Alice", "a@x.com", "t1")
        val bob = Player("b", "Bob", "b@x.com", "t1")
        val teamSessions =
            (6L downTo 1L).map { session(it, made = 7, attempted = 10).copy(playerId = "a", id = "a$it") } +
                ((10L downTo 6L).map { session(it, made = 6, attempted = 10).copy(playerId = "b", id = "b$it") } +
                    (5L downTo 1L).map { session(it, made = 3, attempted = 10).copy(playerId = "b", id = "bb$it") })

        val summaries = StatsCalculator.playerSummaries(listOf(bob, alice), teamSessions)
        assertEquals("Alice", summaries.first().player.displayName)
        assertFalse(summaries.first().needsAttention)
        val bobSummary = summaries.last()
        assertTrue(bobSummary.needsAttention)
        assertEquals(-30.0, bobSummary.deltaPct, 0.001)
    }

    @Test
    fun `insights and recommendations never return empty lists`() {
        assertTrue(StatsCalculator.insights(PlayerStats.EMPTY).isNotEmpty())
        assertTrue(StatsCalculator.coachRecommendations(emptyList()).isNotEmpty())
        val stats = StatsCalculator.compute(listOf(session(1, 5, 10)), now)
        assertTrue(StatsCalculator.insights(stats).isNotEmpty())
        assertTrue(
            StatsCalculator.sessionFeedback(session(0, 8, 10), isPersonalBest = true)
                .first().contains("personal best"),
        )
    }
}
