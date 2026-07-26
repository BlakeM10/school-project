package com.nextgen.courtvision.viewmodel

import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.model.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CoachDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreRepository: FirestoreRepository

    private val coachNoTeam = Coach("coach-1", "Coach Banda", "b@example.com", teamId = null)
    private val coachWithTeam = coachNoTeam.copy(teamId = "team-1")
    private val team = Team("team-1", "NextGen U18", "K7PQ2M", listOf("coach-1"), listOf("p1"))
    private val playerOne = Player("p1", "Martin", "m@example.com", "team-1")
    private val playerTwo = Player("p2", "Chisomo", "c@example.com", "team-1")

    private fun session(id: String, playerId: String, accuracy: Double) = Session(
        id = id,
        playerId = playerId,
        teamId = "team-1",
        drillId = "free_throw_series",
        startedAtMillis = 1_000_000L,
        durationSec = 300,
        shotsAttempted = 10,
        shotsMade = (accuracy / 10).toInt(),
        accuracyPct = accuracy,
    )

    @Before
    fun setUp() {
        authRepository = mock()
        firestoreRepository = mock()
        // Team analytics stream; empty by default.
        whenever(firestoreRepository.observeTeamSessions(any())).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `coach without a team is prompted to create one`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(coachNoTeam)

            val vm = CoachDashboardViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.needsTeam)
            assertEquals(null, vm.uiState.value.team)
        }

    @Test
    fun `creating a team shows the join code and starts observing the roster`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser())
                .thenReturn(coachNoTeam)
                .thenReturn(coachWithTeam)
            whenever(firestoreRepository.createTeam(any(), any())).thenReturn(Result.success(team))
            whenever(firestoreRepository.observeTeamPlayers("team-1"))
                .thenReturn(flowOf(listOf(playerOne)))
            whenever(firestoreRepository.observeSessions("p1"))
                .thenReturn(flowOf(listOf(session("s1", "p1", 70.0))))

            val vm = CoachDashboardViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()
            vm.createTeam("NextGen U18")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("K7PQ2M", state.team?.joinCode)
            assertEquals(listOf(playerOne), state.players)
            // First player is auto-selected so the chart has data immediately
            assertEquals(playerOne, state.selectedPlayer)
            assertEquals(1, state.selectedPlayerSessions.size)
        }

    @Test
    fun `blank team name is rejected before hitting the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(coachNoTeam)

            val vm = CoachDashboardViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()
            vm.createTeam("   ")
            advanceUntilIdle()

            assertEquals("Enter a team name", vm.uiState.value.error)
        }

    @Test
    fun `selecting a player switches the observed session stream`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(coachWithTeam)
            whenever(firestoreRepository.getTeam("team-1")).thenReturn(Result.success(team))
            whenever(firestoreRepository.observeTeamPlayers("team-1"))
                .thenReturn(flowOf(listOf(playerOne, playerTwo)))
            whenever(firestoreRepository.observeSessions("p1"))
                .thenReturn(flowOf(listOf(session("s1", "p1", 70.0))))
            whenever(firestoreRepository.observeSessions("p2"))
                .thenReturn(flowOf(listOf(session("s2", "p2", 40.0), session("s3", "p2", 55.0))))

            val vm = CoachDashboardViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()

            assertEquals(playerOne, vm.uiState.value.selectedPlayer)

            vm.selectPlayer(playerTwo)
            advanceUntilIdle()

            assertEquals(playerTwo, vm.uiState.value.selectedPlayer)
            assertEquals(2, vm.uiState.value.selectedPlayerSessions.size)
        }

    @Test
    fun `team load failure surfaces an error`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(coachWithTeam)
        whenever(firestoreRepository.getTeam("team-1"))
            .thenReturn(Result.failure(IllegalStateException("offline and no cache")))

        val vm = CoachDashboardViewModel(authRepository, firestoreRepository)
        advanceUntilIdle()

        assertEquals("offline and no cache", vm.uiState.value.error)
    }
}
