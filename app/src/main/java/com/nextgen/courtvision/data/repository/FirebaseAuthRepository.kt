package com.nextgen.courtvision.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nextgen.courtvision.data.mapper.UserMapper
import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Role
import com.nextgen.courtvision.domain.model.User
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override suspend fun signInWithEmail(email: String, password: String): Result<User> =
        runCatching {
            val firebaseUser = auth.signInWithEmailAndPassword(email, password).await().user
                ?: error("Authentication returned no user")
            fetchProfile(firebaseUser.uid)
                ?: error("This account has no Court Vision profile")
        }

    override suspend fun registerWithEmail(
        displayName: String,
        email: String,
        password: String,
        role: Role,
    ): Result<User> = runCatching {
        val firebaseUser = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Registration returned no user")
        createProfile(firebaseUser.uid, displayName, email, role)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInResult> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val firebaseUser = auth.signInWithCredential(credential).await().user
                ?: error("Google authentication returned no user")
            val profile = fetchProfile(firebaseUser.uid)
            if (profile != null) {
                GoogleSignInResult.Success(profile)
            } else {
                GoogleSignInResult.ProfileMissing(
                    suggestedName = firebaseUser.displayName.orEmpty(),
                    email = firebaseUser.email.orEmpty(),
                )
            }
        }

    override suspend fun completeProfile(displayName: String, role: Role): Result<User> =
        runCatching {
            val firebaseUser = auth.currentUser ?: error("Not signed in")
            createProfile(firebaseUser.uid, displayName, firebaseUser.email.orEmpty(), role)
        }

    override suspend fun loadCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return runCatching { fetchProfile(firebaseUser.uid) }.getOrNull()
    }

    override fun signOut() {
        auth.signOut()
    }

    private suspend fun fetchProfile(uid: String): User? {
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        return snapshot.data?.let { UserMapper.fromDocument(uid, it) }
    }

    private suspend fun createProfile(
        uid: String,
        displayName: String,
        email: String,
        role: Role,
    ): User {
        val user: User = when (role) {
            Role.PLAYER -> Player(uid, displayName.trim(), email)
            Role.COACH -> Coach(uid, displayName.trim(), email)
        }
        // createdAt is server-generated; the security rules require it on create.
        val document = UserMapper.toDocument(user) + mapOf("createdAt" to FieldValue.serverTimestamp())
        firestore.collection(USERS_COLLECTION).document(uid).set(document).await()
        return user
    }

    private companion object {
        const val USERS_COLLECTION = "users"
    }
}
