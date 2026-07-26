package com.nextgen.courtvision.viewmodel

import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DrillLibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreRepository: FirestoreRepository

    private val drill = Drill(
        id = "d1",
        name = "Free Throws",
        category = DrillCategory.SHOOTING,
        instructions = "",
        targetMetrics = emptyMap(),
        durationSec = 300,
        measures = listOf(Measure.SHOTS),
        sortOrder = 1,
    )

    private val playerNoTeam = Player("uid-1", "Martin", "m@example.com", teamId = null)
    private val playerWithTeam = playerNoTeam.copy(teamId = "team-1")
    private val team = Team("team-1", "NextGen U18", "K7PQ2M", listOf("coach-1"), listOf("uid-1"))

    @Before
    fun setUp() {
        authRepository = mock()
        firestoreRepository = mock()
    }

    @Test
    fun `loads drills and flags missing team`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(playerNoTeam)
        whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))

        val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf(drill), state.drills)
        assertTrue(state.needsTeam)
    }

    @Test
    fun `player already on a team does not see the banner`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(playerWithTeam)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))

            val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.needsTeam)
        }

    @Test
    fun `drill load failure surfaces an error`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(playerWithTeam)
        whenever(firestoreRepository.getDrills())
            .thenReturn(Result.failure(IllegalStateException("offline and no cache")))

        val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
        advanceUntilIdle()

        assertEquals("offline and no cache", viewModel.uiState.value.error)
    }

    @Test
    fun `successful team join refreshes the profile`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser())
            .thenReturn(playerNoTeam)
            .thenReturn(playerWithTeam)
        whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
        whenever(firestoreRepository.joinTeam(any(), any())).thenReturn(Result.success(team))

        val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
        advanceUntilIdle()
        viewModel.joinTeam("K7PQ2M")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.joiningTeam)
        assertFalse(state.needsTeam)
        assertEquals("team-1", state.user?.teamId)
    }

    @Test
    fun `failed team join surfaces the error and keeps the banner`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(playerNoTeam)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
            whenever(firestoreRepository.joinTeam(any(), any()))
                .thenReturn(Result.failure(IllegalStateException("No team found")))

            val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
            advanceUntilIdle()
            viewModel.joinTeam("WRONG1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.error)
            assertTrue(state.needsTeam)
        }

    @Test
    fun `join without a signed-in user is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(null)
        whenever(firestoreRepository.getDrills()).thenReturn(Result.success(emptyList()))

        val viewModel = DrillLibraryViewModel(authRepository, firestoreRepository)
        advanceUntilIdle()
        viewModel.joinTeam("K7PQ2M")
        advanceUntilIdle()

        verify(firestoreRepository, never()).joinTeam(any(), any())
    }
}
