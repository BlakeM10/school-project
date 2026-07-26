package com.nextgen.courtvision.data.mapper

import com.google.firebase.Timestamp
import com.nextgen.courtvision.domain.model.Session
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMapperTest {

    private val startedAt = 1_750_000_000_000L

    private val session = Session(
        id = "s1",
        playerId = "uid-1",
        teamId = "team-1",
        drillId = "free_throw_series",
        startedAtMillis = startedAt,
        durationSec = 300,
        shotsAttempted = 20,
        shotsMade = 13,
        accuracyPct = 65.0,
        releaseTimesMs = listOf(720L, 680L),
        avgReleaseTimeMs = 700.0,
        dribbleCount = 0,
        dribbleSpeedHz = 0.0,
        reactionTimesMs = emptyList(),
        avgReactionTimeMs = 0.0,
        appVersion = "0.1.0",
    )

    @Test
    fun `round trip preserves every field`() {
        val doc = SessionMapper.toDocument(session)
        val restored = SessionMapper.fromDocument("s1", doc)!!
        assertEquals(session, restored)
    }

    @Test
    fun `startedAt is stored as a Firestore timestamp`() {
        val doc = SessionMapper.toDocument(session)
        assertEquals(Timestamp(Date(startedAt)), doc["startedAt"])
    }

    @Test
    fun `numeric fields survive firestore Long round trip`() {
        val doc = SessionMapper.toDocument(session)
            .mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        val restored = SessionMapper.fromDocument("s1", doc)!!
        assertEquals(20, restored.shotsAttempted)
        assertEquals(300, restored.durationSec)
    }

    @Test
    fun `null teamId is preserved for players not yet on a team`() {
        val doc = SessionMapper.toDocument(session.copy(teamId = null))
        assertNull(SessionMapper.fromDocument("s1", doc)!!.teamId)
    }

    @Test
    fun `documents missing identity fields are rejected`() {
        val doc = SessionMapper.toDocument(session)
        assertNull(SessionMapper.fromDocument("s1", doc - "playerId"))
        assertNull(SessionMapper.fromDocument("s1", doc - "drillId"))
        assertNull(SessionMapper.fromDocument("s1", doc - "startedAt"))
    }

    @Test
    fun `derived metrics computed from raw values`() {
        val raw = session.copy(accuracyPct = 0.0, avgReleaseTimeMs = 0.0, avgReactionTimeMs = 0.0)
        val derived = Session.withDerivedMetrics(raw)
        assertEquals(65.0, derived.accuracyPct, 0.001)
        assertEquals(700.0, derived.avgReleaseTimeMs, 0.001)
        assertEquals(0.0, derived.avgReactionTimeMs, 0.001)
    }

    @Test
    fun `derived metrics with zero attempts do not divide by zero`() {
        val raw = session.copy(shotsAttempted = 0, shotsMade = 0, releaseTimesMs = emptyList())
        val derived = Session.withDerivedMetrics(raw)
        assertEquals(0.0, derived.accuracyPct, 0.0)
        assertEquals(0.0, derived.avgReleaseTimeMs, 0.0)
    }
}
