package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamMapperTest {

    private val team = Team(
        id = "team-1",
        name = "NextGen U18",
        joinCode = "K7PQ2M",
        coachIds = listOf("coach-1"),
        playerIds = listOf("player-1", "player-2"),
    )

    @Test
    fun `round trip preserves every field`() {
        val doc = TeamMapper.toDocument(team)
        assertEquals(team, TeamMapper.fromDocument("team-1", doc))
    }

    @Test
    fun `empty member lists map to empty lists`() {
        val doc = TeamMapper.toDocument(team.copy(coachIds = emptyList(), playerIds = emptyList()))
        val restored = TeamMapper.fromDocument("team-1", doc)!!
        assertTrue(restored.coachIds.isEmpty())
        assertTrue(restored.playerIds.isEmpty())
    }

    @Test
    fun `missing name or joinCode is rejected`() {
        val doc = TeamMapper.toDocument(team)
        assertNull(TeamMapper.fromDocument("team-1", doc - "name"))
        assertNull(TeamMapper.fromDocument("team-1", doc - "joinCode"))
    }
}
