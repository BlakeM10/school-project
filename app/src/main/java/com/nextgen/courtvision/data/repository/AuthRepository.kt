package com.nextgen.courtvision.data.repository

import com.nextgen.courtvision.domain.model.Role
import com.nextgen.courtvision.domain.model.User

/** Outcome of a Google sign-in: an account may authenticate successfully but not
 * yet have a `users/{uid}` profile (first-time Google users must pick a role). */
sealed class GoogleSignInResult {
    data class Success(val user: User) : GoogleSignInResult()
    data class ProfileMissing(val suggestedName: String, val email: String) : GoogleSignInResult()
}

interface AuthRepository {

    suspend fun signInWithEmail(email: String, password: String): Result<User>

    suspend fun registerWithEmail(
        displayName: String,
        email: String,
        password: String,
        role: Role,
    ): Result<User>

    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInResult>

    /** Creates the `users/{uid}` profile for an already-authenticated account
     *  (the second step of a first-time Google sign-in). */
    suspend fun completeProfile(displayName: String, role: Role): Result<User>

    /** Restores the signed-in user's profile, or null when signed out. */
    suspend fun loadCurrentUser(): User?

    fun signOut()
}
