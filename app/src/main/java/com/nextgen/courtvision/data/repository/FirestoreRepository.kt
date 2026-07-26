package com.nextgen.courtvision.data.repository

import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.model.Team
import kotlinx.coroutines.flow.Flow

/**
 * The app's data API over Firestore (the FirestoreRepository class from the
 * proposal's class model). Suspend functions are one-shot operations that
 * respect the SDK's offline queue; Flows are live snapshot streams that emit
 * again whenever the underlying documents change (including local-only,
 * not-yet-synced writes recorded at the gym).
 */
interface FirestoreRepository {

    // Drills — read-only catalogue
    suspend fun getDrills(): Result<List<Drill>>

    // Sessions
    suspend fun saveSession(session: Session): Result<String>
    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    fun observeSessions(playerId: String): Flow<List<Session>>
    fun observeSessionsForDrill(playerId: String, drillId: String): Flow<List<Session>>
    fun observeTeamSessions(teamId: String): Flow<List<Session>>

    // Teams
    suspend fun createTeam(name: String, coachId: String): Result<Team>
    suspend fun joinTeam(joinCode: String, playerId: String): Result<Team>
    suspend fun getTeam(teamId: String): Result<Team>
    fun observeTeamPlayers(teamId: String): Flow<List<Player>>
}
