package com.nextgen.courtvision.domain.session

import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Session

/**
 * Aggregates detection events from the CV pipeline into an immutable Session
 * (the SessionRecorder class from the proposal's class model). Pure Kotlin —
 * no Android or Firebase types — so the whole recording lifecycle is unit
 * testable. The clock is injectable for deterministic tests.
 */
class SessionRecorder(
    private val playerId: String,
    private val teamId: String?,
    private val drill: Drill,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var startedAtMillis: Long = 0L
    private var running = false

    private var shotsAttempted = 0
    private var shotsMade = 0
    private val releaseTimesMs = mutableListOf<Long>()
    private val dribbleIntervalsMs = mutableListOf<Long>()
    private val reactionTimesMs = mutableListOf<Long>()

    val isRunning: Boolean get() = running

    fun start() {
        check(!running) { "Recorder already started" }
        startedAtMillis = clock()
        running = true
    }

    fun recordShot(made: Boolean, releaseTimeMs: Long) {
        requireRunning()
        shotsAttempted++
        if (made) shotsMade++
        if (releaseTimeMs > 0) releaseTimesMs += releaseTimeMs
    }

    fun recordDribble(intervalMs: Long) {
        requireRunning()
        dribbleIntervalsMs += intervalMs
    }

    fun recordReaction(reactionMs: Long) {
        requireRunning()
        reactionTimesMs += reactionMs
    }

    /** Live snapshot for the on-screen HUD. */
    fun currentStats(): LiveStats = LiveStats(
        shotsAttempted = shotsAttempted,
        shotsMade = shotsMade,
        dribbleCount = dribbleIntervalsMs.size,
        reactionCount = reactionTimesMs.size,
        elapsedSec = if (running) ((clock() - startedAtMillis) / 1000).toInt() else 0,
    )

    fun finish(): Session {
        requireRunning()
        running = false
        val durationSec = ((clock() - startedAtMillis) / 1000).toInt()
        val meanIntervalMs = dribbleIntervalsMs.average()
        return Session.withDerivedMetrics(
            Session(
                id = "",
                playerId = playerId,
                teamId = teamId,
                drillId = drill.id,
                startedAtMillis = startedAtMillis,
                durationSec = durationSec,
                shotsAttempted = shotsAttempted,
                shotsMade = shotsMade,
                releaseTimesMs = releaseTimesMs.toList(),
                dribbleCount = dribbleIntervalsMs.size,
                // bounces per second from the mean inter-bounce interval,
                // per the proposal's dribble speed definition
                dribbleSpeedHz = if (meanIntervalMs > 0) 1000.0 / meanIntervalMs else 0.0,
                reactionTimesMs = reactionTimesMs.toList(),
                appVersion = appVersion,
            ),
        )
    }

    private fun requireRunning() = check(running) { "Recorder is not running" }

    data class LiveStats(
        val shotsAttempted: Int,
        val shotsMade: Int,
        val dribbleCount: Int,
        val reactionCount: Int,
        val elapsedSec: Int,
    )
}
