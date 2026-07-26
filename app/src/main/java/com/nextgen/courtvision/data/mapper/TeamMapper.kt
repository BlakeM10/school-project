package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.Team

/** Maps `teams/{teamId}` documents (docs/DATABASE_SCHEMA.md §2.2). */
object TeamMapper {

    fun fromDocument(id: String, data: Map<String, Any?>): Team? {
        val name = data["name"] as? String ?: return null
        val joinCode = data["joinCode"] as? String ?: return null
        return Team(
            id = id,
            name = name,
            joinCode = joinCode,
            coachIds = stringList(data["coachIds"]),
            playerIds = stringList(data["playerIds"]),
        )
    }

    fun toDocument(team: Team): Map<String, Any?> = mapOf(
        "name" to team.name,
        "joinCode" to team.joinCode,
        "coachIds" to team.coachIds,
        "playerIds" to team.playerIds,
    )

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>).orEmpty().filterIsInstance<String>()
}
