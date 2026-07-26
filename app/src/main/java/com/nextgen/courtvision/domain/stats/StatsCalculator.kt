package com.nextgen.courtvision.domain.stats

import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StatsPeriod(val label: String, val windowDays: Int?) {
    WEEK("Weekly", 7),
    MONTH("Monthly", 30),
    YEAR("Yearly", 365),
    ALL("All time", null),
}

data class PlayerStats(
    val sessionCount: Int,
    val totalShots: Int,
    val totalMade: Int,
    val avgAccuracyPct: Double,
    val bestAccuracyPct: Double,
    val streakDays: Int,
    val trainedToday: Boolean,
    val personalBest: Session?,
    /** Mean accuracy of the latest sessions minus the preceding block (± pct points). */
    val accuracyDeltaPct: Double,
) {
    companion object {
        val EMPTY = PlayerStats(0, 0, 0, 0.0, 0.0, 0, false, null, 0.0)
    }
}

data class PlayerSummary(
    val player: Player,
    val sessionCount: Int,
    val avgAccuracyPct: Double,
    val deltaPct: Double,
) {
    val improving: Boolean get() = deltaPct >= IMPROVING_THRESHOLD
    val needsAttention: Boolean get() = deltaPct <= ATTENTION_THRESHOLD

    private companion object {
        const val IMPROVING_THRESHOLD = 2.0
        const val ATTENTION_THRESHOLD = -2.0
    }
}

/**
 * All derived analytics shown on the dashboards. Pure functions over the
 * session history — every number on screen is computed from recorded data,
 * never invented. Time is injected (epoch millis) for deterministic tests;
 * "days" are UTC day buckets, which is adequate for streak display.
 */
object StatsCalculator {

    private const val DAY_MS = 86_400_000L
    private const val TREND_WINDOW = 5

    fun filterByPeriod(
        sessions: List<Session>,
        period: StatsPeriod,
        nowMillis: Long,
    ): List<Session> {
        val days = period.windowDays ?: return sessions
        val cutoff = nowMillis - days * DAY_MS
        return sessions.filter { it.startedAtMillis >= cutoff }
    }

    fun compute(sessions: List<Session>, nowMillis: Long): PlayerStats {
        if (sessions.isEmpty()) return PlayerStats.EMPTY

        val shooting = sessions.filter { it.shotsAttempted > 0 }
        val avg = shooting.map { it.accuracyPct }.averageOrZero()
        val best = shooting.maxOfOrNull { it.accuracyPct } ?: 0.0

        val dayBuckets = sessions.map { it.startedAtMillis / DAY_MS }.toSortedSet()
        val today = nowMillis / DAY_MS
        val trainedToday = today in dayBuckets

        // Streak: consecutive training days ending today (or yesterday, so an
        // active streak isn't shown as broken before today's session).
        var streak = 0
        var cursor = if (trainedToday) today else today - 1
        while (cursor in dayBuckets) {
            streak++
            cursor--
        }

        return PlayerStats(
            sessionCount = sessions.size,
            totalShots = shooting.sumOf { it.shotsAttempted },
            totalMade = shooting.sumOf { it.shotsMade },
            avgAccuracyPct = avg,
            bestAccuracyPct = best,
            streakDays = streak,
            trainedToday = trainedToday,
            personalBest = shooting.maxByOrNull { it.accuracyPct },
            accuracyDeltaPct = accuracyDelta(shooting),
        )
    }

    /** Rule-based insight lines for the player dashboard and session summary. */
    fun insights(stats: PlayerStats): List<String> {
        if (stats.sessionCount == 0) {
            return listOf("Record your first drill to start building your performance profile.")
        }
        val lines = mutableListOf<String>()
        when {
            stats.accuracyDeltaPct >= 2.0 ->
                lines += "Shooting accuracy is trending up ${stats.accuracyDeltaPct.roundToInt()} points across your recent sessions — keep this rhythm."
            stats.accuracyDeltaPct <= -2.0 ->
                lines += "Accuracy dipped ${abs(stats.accuracyDeltaPct).roundToInt()} points recently. Slow the tempo and focus on form for a session."
            else ->
                lines += "Accuracy is holding steady around ${stats.avgAccuracyPct.roundToInt()}%. Add volume to push through the plateau."
        }
        if (stats.streakDays >= 3) {
            lines += "${stats.streakDays}-day training streak — consistency is the fastest path to improvement."
        } else if (!stats.trainedToday) {
            lines += "No session yet today. Even a short drill keeps the habit alive."
        }
        stats.personalBest?.let {
            lines += "Personal best: ${it.accuracyPct.roundToInt()}% accuracy. Beat it in your next shooting drill."
        }
        return lines
    }

    /** Per-session feedback for the session-complete screen. */
    fun sessionFeedback(session: Session, isPersonalBest: Boolean): List<String> {
        val lines = mutableListOf<String>()
        if (session.shotsAttempted > 0) {
            when {
                isPersonalBest -> lines += "New personal best — your highest recorded accuracy so far."
                session.accuracyPct >= 70 -> lines += "Elite shooting session: ${session.accuracyPct.roundToInt()}% is well above your target range."
                session.accuracyPct >= 50 -> lines += "Solid shooting session. Groove the same release to push past ${session.accuracyPct.roundToInt()}%."
                else -> lines += "Tough shooting day — shorten the range and rebuild rhythm before adding distance."
            }
            if (session.avgReleaseTimeMs in 1.0..650.0) {
                lines += "Quick release (${session.avgReleaseTimeMs.roundToInt()} ms) — defenders will struggle with that."
            } else if (session.avgReleaseTimeMs > 900) {
                lines += "Release time averaged ${session.avgReleaseTimeMs.roundToInt()} ms — work on catching ready to shoot."
            }
        }
        if (session.dribbleCount > 0 && session.dribbleSpeedHz >= 2.5) {
            lines += "Dribble speed of ${"%.1f".format(session.dribbleSpeedHz)} bounces/sec shows strong ball control."
        }
        if (session.reactionTimesMs.isNotEmpty() && session.avgReactionTimeMs in 1.0..400.0) {
            lines += "Average reaction of ${session.avgReactionTimeMs.roundToInt()} ms is quicker than most guards."
        }
        if (lines.isEmpty()) lines += "Session recorded. Keep stacking reps — trends need data."
        return lines
    }

    /** Ranked roster analytics for the coach dashboard. */
    fun playerSummaries(players: List<Player>, teamSessions: List<Session>): List<PlayerSummary> {
        val byPlayer = teamSessions.groupBy { it.playerId }
        return players.map { player ->
            val sessions = byPlayer[player.uid].orEmpty().sortedBy { it.startedAtMillis }
            val shooting = sessions.filter { it.shotsAttempted > 0 }
            PlayerSummary(
                player = player,
                sessionCount = sessions.size,
                avgAccuracyPct = shooting.map { it.accuracyPct }.averageOrZero(),
                deltaPct = accuracyDelta(shooting),
            )
        }.sortedWith(compareByDescending<PlayerSummary> { it.avgAccuracyPct }.thenBy { it.player.displayName })
    }

    fun coachRecommendations(summaries: List<PlayerSummary>): List<String> {
        if (summaries.isEmpty()) return listOf("Share the team code so players can join and start recording sessions.")
        val lines = mutableListOf<String>()
        summaries.filter { it.needsAttention }.take(2).forEach {
            lines += "${it.player.displayName}'s accuracy is trending down ${abs(it.deltaPct).roundToInt()} points — schedule a form-focused session."
        }
        summaries.filter { it.improving }.take(2).forEach {
            lines += "${it.player.displayName} is up ${it.deltaPct.roundToInt()} points — reinforce what's working."
        }
        summaries.filter { it.sessionCount == 0 }.take(1).forEach {
            lines += "${it.player.displayName} hasn't recorded a session yet — check they have the app set up."
        }
        if (lines.isEmpty()) lines += "The squad is stable. Introduce a harder drill to create a new stimulus."
        return lines
    }

    private fun accuracyDelta(shootingSessions: List<Session>): Double {
        val ordered = shootingSessions.sortedBy { it.startedAtMillis }
        if (ordered.size < 2) return 0.0
        val recent = ordered.takeLast(TREND_WINDOW)
        val previous = ordered.dropLast(recent.size).takeLast(TREND_WINDOW)
        if (previous.isEmpty()) return 0.0
        return recent.map { it.accuracyPct }.averageOrZero() -
            previous.map { it.accuracyPct }.averageOrZero()
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
