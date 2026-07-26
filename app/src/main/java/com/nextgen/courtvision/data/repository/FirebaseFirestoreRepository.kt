package com.nextgen.courtvision.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nextgen.courtvision.data.mapper.DrillMapper
import com.nextgen.courtvision.data.mapper.SessionMapper
import com.nextgen.courtvision.data.mapper.TeamMapper
import com.nextgen.courtvision.data.mapper.UserMapper
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.model.Team
import com.nextgen.courtvision.domain.team.JoinCodeGenerator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseFirestoreRepository(
    private val firestore: FirebaseFirestore,
) : FirestoreRepository {

    override suspend fun getDrills(): Result<List<Drill>> = runCatching {
        firestore.collection(DRILLS).orderBy("sortOrder").get().await()
            .documents.mapNotNull { doc -> doc.data?.let { DrillMapper.fromDocument(doc.id, it) } }
    }

    override suspend fun saveSession(session: Session): Result<String> = runCatching {
        val ref = firestore.collection(SESSIONS).document()
        ref.set(SessionMapper.toDocument(session)).await()
        ref.id
    }

    override suspend fun getSession(sessionId: String): Result<Session> = runCatching {
        val doc = firestore.collection(SESSIONS).document(sessionId).get().await()
        doc.data?.let { SessionMapper.fromDocument(doc.id, it) }
            ?: error("Session $sessionId not found")
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        firestore.collection(SESSIONS).document(sessionId).delete().await()
    }

    override fun observeSessions(playerId: String): Flow<List<Session>> =
        sessionQueryFlow(
            firestore.collection(SESSIONS)
                .whereEqualTo("playerId", playerId)
                .orderBy("startedAt", Query.Direction.DESCENDING),
        )

    override fun observeSessionsForDrill(playerId: String, drillId: String): Flow<List<Session>> =
        sessionQueryFlow(
            firestore.collection(SESSIONS)
                .whereEqualTo("playerId", playerId)
                .whereEqualTo("drillId", drillId)
                .orderBy("startedAt", Query.Direction.DESCENDING),
        )

    override fun observeTeamSessions(teamId: String): Flow<List<Session>> =
        sessionQueryFlow(
            firestore.collection(SESSIONS)
                .whereEqualTo("teamId", teamId)
                .orderBy("startedAt", Query.Direction.DESCENDING),
        )

    override suspend fun createTeam(name: String, coachId: String): Result<Team> = runCatching {
        val ref = firestore.collection(TEAMS).document()
        val team = Team(
            id = ref.id,
            name = name.trim(),
            joinCode = JoinCodeGenerator.generate(),
            coachIds = listOf(coachId),
            playerIds = emptyList(),
        )
        // Team creation and the coach's own teamId update commit atomically so a
        // coach can never end up owning a team their profile doesn't reference.
        firestore.batch().apply {
            set(ref, TeamMapper.toDocument(team) + mapOf("createdAt" to FieldValue.serverTimestamp()))
            update(firestore.collection(USERS).document(coachId), "teamId", ref.id)
        }.commit().await()
        team
    }

    override suspend fun joinTeam(joinCode: String, playerId: String): Result<Team> = runCatching {
        val code = JoinCodeGenerator.normalise(joinCode)
        require(code.length == JoinCodeGenerator.CODE_LENGTH) { "Enter the $CODE_LENGTH_TEXT team code" }

        val snapshot = firestore.collection(TEAMS)
            .whereEqualTo("joinCode", code)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull()
            ?: error("No team found for code $code — check it with your coach")
        val team = doc.data?.let { TeamMapper.fromDocument(doc.id, it) }
            ?: error("Team record is malformed")

        // The security rules only allow a player to add their own uid to playerIds.
        firestore.batch().apply {
            update(doc.reference, "playerIds", FieldValue.arrayUnion(playerId))
            update(firestore.collection(USERS).document(playerId), "teamId", doc.id)
        }.commit().await()

        team.copy(playerIds = team.playerIds + playerId)
    }

    override suspend fun getTeam(teamId: String): Result<Team> = runCatching {
        val doc = firestore.collection(TEAMS).document(teamId).get().await()
        doc.data?.let { TeamMapper.fromDocument(doc.id, it) }
            ?: error("Team $teamId not found")
    }

    override fun observeTeamPlayers(teamId: String): Flow<List<Player>> = callbackFlow {
        val registration = firestore.collection(USERS)
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("role", "player")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val players = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.data?.let { UserMapper.fromDocument(doc.id, it) } as? Player
                }
                trySend(players)
            }
        awaitClose { registration.remove() }
    }

    private fun sessionQueryFlow(query: Query): Flow<List<Session>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot: QuerySnapshot?, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val sessions = snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.data?.let { SessionMapper.fromDocument(doc.id, it) }
            }
            trySend(sessions)
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val USERS = "users"
        const val TEAMS = "teams"
        const val DRILLS = "drills"
        const val SESSIONS = "sessions"
        const val CODE_LENGTH_TEXT = "6-character"
    }
}
