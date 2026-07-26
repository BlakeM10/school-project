package com.nextgen.courtvision.domain.model

/**
 * Role names match the wire format stored in `users.role` and checked by the
 * Firestore security rules — do not rename without migrating both.
 */
enum class Role(val wireName: String) {
    PLAYER("player"),
    COACH("coach");

    companion object {
        fun fromWire(value: String?): Role? = entries.firstOrNull { it.wireName == value }
    }
}

sealed class User {
    abstract val uid: String
    abstract val displayName: String
    abstract val email: String
    abstract val teamId: String?

    val role: Role
        get() = when (this) {
            is Player -> Role.PLAYER
            is Coach -> Role.COACH
        }
}

data class Player(
    override val uid: String,
    override val displayName: String,
    override val email: String,
    override val teamId: String? = null,
) : User()

data class Coach(
    override val uid: String,
    override val displayName: String,
    override val email: String,
    override val teamId: String? = null,
) : User()
