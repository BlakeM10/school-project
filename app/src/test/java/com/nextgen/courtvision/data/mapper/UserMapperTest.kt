package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMapperTest {

    private val playerDoc = mapOf(
        "role" to "player",
        "displayName" to "Martin Blake",
        "email" to "martin@example.com",
        "teamId" to "team-1",
    )

    @Test
    fun `maps player document to Player`() {
        val user = UserMapper.fromDocument("uid-1", playerDoc)
        assertTrue(user is Player)
        assertEquals("uid-1", user!!.uid)
        assertEquals("Martin Blake", user.displayName)
        assertEquals("team-1", user.teamId)
    }

    @Test
    fun `maps coach document to Coach`() {
        val user = UserMapper.fromDocument("uid-2", playerDoc + mapOf("role" to "coach"))
        assertTrue(user is Coach)
    }

    @Test
    fun `null teamId is preserved`() {
        val user = UserMapper.fromDocument("uid-1", playerDoc + mapOf("teamId" to null))
        assertNull(user!!.teamId)
    }

    @Test
    fun `unknown role yields null instead of a wrong default`() {
        assertNull(UserMapper.fromDocument("uid-1", playerDoc + mapOf("role" to "admin")))
        assertNull(UserMapper.fromDocument("uid-1", playerDoc - "role"))
    }

    @Test
    fun `missing required fields yield null`() {
        assertNull(UserMapper.fromDocument("uid-1", playerDoc - "displayName"))
        assertNull(UserMapper.fromDocument("uid-1", playerDoc - "email"))
    }

    @Test
    fun `round trip preserves wire format`() {
        val user = UserMapper.fromDocument("uid-1", playerDoc)!!
        val doc = UserMapper.toDocument(user)
        assertEquals("player", doc["role"])
        assertEquals("Martin Blake", doc["displayName"])
        assertEquals("martin@example.com", doc["email"])
        assertEquals("team-1", doc["teamId"])
    }
}
