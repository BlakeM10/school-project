package com.nextgen.courtvision.domain.model

/**
 * One completed drill run — mirrors `sessions/{sessionId}`
 * (docs/DATABASE_SCHEMA.md §2.4). Immutable record of fact; metric fields not
 * measured by the drill stay at 0 / empty so every session has one shape.
 */
data class Session(
    val id: String,
    val playerId: String,
    val teamId: String?,
    val drillId: String,
    val startedAtMillis: Long,
    val durationSec: Int,
    val shotsAttempted: Int = 0,
    val shotsMade: Int = 0,
    val accuracyPct: Double = 0.0,
    val releaseTimesMs: List<Long> = emptyList(),
    val avgReleaseTimeMs: Double = 0.0,
    val dribbleCount: Int = 0,
    val dribbleSpeedHz: Double = 0.0,
    val reactionTimesMs: List<Long> = emptyList(),
    val avgReactionTimeMs: Double = 0.0,
    val appVersion: String = "",
) {
    companion object {
        /** Computes the derived aggregates the schema stores alongside raw values. */
        fun withDerivedMetrics(session: Session): Session = session.copy(
            accuracyPct = if (session.shotsAttempted > 0) {
                session.shotsMade * 100.0 / session.shotsAttempted
            } else 0.0,
            avgReleaseTimeMs = session.releaseTimesMs.average().orZero(),
            avgReactionTimeMs = session.reactionTimesMs.average().orZero(),
        )

        private fun Double.orZero(): Double = if (isNaN()) 0.0 else this
    }
}
