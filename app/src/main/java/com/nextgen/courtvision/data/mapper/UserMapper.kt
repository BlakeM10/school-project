package com.nextgen.courtvision.data.mapper

import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Role
import com.nextgen.courtvision.domain.model.User

/**
 * Maps between `users/{uid}` documents (docs/DATABASE_SCHEMA.md §2.1) and domain
 * models. Kept free of Firebase types so it is testable on the JVM; the
 * repository adds server-generated fields (createdAt) at write time.
 */
object UserMapper {

    private const val FIELD_ROLE = "role"
    private const val FIELD_DISPLAY_NAME = "displayName"
    private const val FIELD_EMAIL = "email"
    private const val FIELD_TEAM_ID = "teamId"

    fun fromDocument(uid: String, data: Map<String, Any?>): User? {
        val role = Role.fromWire(data[FIELD_ROLE] as? String) ?: return null
        val displayName = data[FIELD_DISPLAY_NAME] as? String ?: return null
        val email = data[FIELD_EMAIL] as? String ?: return null
        val teamId = data[FIELD_TEAM_ID] as? String
        return when (role) {
            Role.PLAYER -> Player(uid, displayName, email, teamId)
            Role.COACH -> Coach(uid, displayName, email, teamId)
        }
    }

    fun toDocument(user: User): Map<String, Any?> = mapOf(
        FIELD_ROLE to user.role.wireName,
        FIELD_DISPLAY_NAME to user.displayName,
        FIELD_EMAIL to user.email,
        FIELD_TEAM_ID to user.teamId,
    )
}
