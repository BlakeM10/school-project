package com.nextgen.courtvision.data.mapper

import com.google.firebase.Timestamp
import com.nextgen.courtvision.domain.model.Session
import java.util.Date

/**
 * Maps between `sessions/{sessionId}` documents (docs/DATABASE_SCHEMA.md §2.4)
 * and the domain model. Timestamps cross the boundary as epoch millis so the
 * domain stays free of Firebase types.
 */
object SessionMapper {

    fun fromDocument(id: String, data: Map<String, Any?>): Session? {
        val playerId = data["playerId"] as? String ?: return null
        val drillId = data["drillId"] as? String ?: return null
        val startedAt = (data["startedAt"] as? Timestamp)?.toDate()?.time ?: return null

        return Session(
            id = id,
            playerId = playerId,
            teamId = data["teamId"] as? String,
            drillId = drillId,
            startedAtMillis = startedAt,
            durationSec = (data["durationSec"] as? Number)?.toInt() ?: 0,
            shotsAttempted = (data["shotsAttempted"] as? Number)?.toInt() ?: 0,
            shotsMade = (data["shotsMade"] as? Number)?.toInt() ?: 0,
            accuracyPct = (data["accuracyPct"] as? Number)?.toDouble() ?: 0.0,
            releaseTimesMs = longList(data["releaseTimesMs"]),
            avgReleaseTimeMs = (data["avgReleaseTimeMs"] as? Number)?.toDouble() ?: 0.0,
            dribbleCount = (data["dribbleCount"] as? Number)?.toInt() ?: 0,
            dribbleSpeedHz = (data["dribbleSpeedHz"] as? Number)?.toDouble() ?: 0.0,
            reactionTimesMs = longList(data["reactionTimesMs"]),
            avgReactionTimeMs = (data["avgReactionTimeMs"] as? Number)?.toDouble() ?: 0.0,
            appVersion = data["appVersion"] as? String ?: "",
        )
    }

    fun toDocument(session: Session): Map<String, Any?> = mapOf(
        "playerId" to session.playerId,
        "teamId" to session.teamId,
        "drillId" to session.drillId,
        "startedAt" to Timestamp(Date(session.startedAtMillis)),
        "durationSec" to session.durationSec,
        "shotsAttempted" to session.shotsAttempted,
        "shotsMade" to session.shotsMade,
        "accuracyPct" to session.accuracyPct,
        "releaseTimesMs" to session.releaseTimesMs,
        "avgReleaseTimeMs" to session.avgReleaseTimeMs,
        "dribbleCount" to session.dribbleCount,
        "dribbleSpeedHz" to session.dribbleSpeedHz,
        "reactionTimesMs" to session.reactionTimesMs,
        "avgReactionTimeMs" to session.avgReactionTimeMs,
        "appVersion" to session.appVersion,
    )

    private fun longList(value: Any?): List<Long> =
        (value as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() }
}
