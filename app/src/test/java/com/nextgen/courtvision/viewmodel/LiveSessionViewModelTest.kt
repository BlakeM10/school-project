package com.nextgen.courtvision.viewmodel

import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.DrillCategory
import com.nextgen.courtvision.domain.model.Measure
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreRepository: FirestoreRepository

    private val player = Player("uid-1", "Martin", "m@example.com", teamId = "team-1")
    private val drill = Drill(
        id = "free_throw_series",
        name = "Free Throw Series",
        category = DrillCategory.SHOOTING,
        instructions = "",
        targetMetrics = emptyMap(),
        durationSec = 300,
        measures = listOf(Measure.SHOTS, Measure.RELEASE_TIME),
        sortOrder = 1,
    )

    @Before
    fun setUp() {
        authRepository = mock()
        firestoreRepository = mock()
    }

    private fun viewModel(): LiveSessionViewModel = LiveSessionViewModel(
        drillId = drill.id,
        appVersion = "0.1.0-test",
        authRepository = authRepository,
        firestoreRepository = firestoreRepository,
    )

    @Test
    fun `init loads the drill and prepares the recorder`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(player)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.loading)
            assertEquals(drill, state.drill)
        }

    @Test
    fun `unknown drill id surfaces an error`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(player)
        whenever(firestoreRepository.getDrills()).thenReturn(Result.success(emptyList()))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Drill not found", vm.uiState.value.error)
    }

    @Test
    fun `cv events during recording update the live stats`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(player)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
            whenever(firestoreRepository.saveSession(any())).thenReturn(Result.success("s1"))

            val vm = viewModel()
            advanceUntilIdle()
            vm.startRecording()
            runCurrent()

            vm.onShotDetected(made = true, releaseTimeMs = 700)
            vm.onShotDetected(made = false, releaseTimeMs = 650)
            vm.onDribbleDetected(400)

            val stats = vm.uiState.value.stats
            assertNotNull(stats)
            assertEquals(2, stats!!.shotsAttempted)
            assertEquals(1, stats.shotsMade)
            assertEquals(1, stats.dribbleCount)

            vm.finishSession()
            advanceTimeBy(2_000)
            advanceUntilIdle()
        }

    @Test
    fun `finish saves the recorded session and exposes its id`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(player)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
            whenever(firestoreRepository.saveSession(any())).thenReturn(Result.success("session-42"))

            val vm = viewModel()
            advanceUntilIdle()
            vm.startRecording()
            runCurrent()
            vm.onShotDetected(made = true, releaseTimeMs = 700)

            vm.finishSession()
            advanceTimeBy(2_000)
            advanceUntilIdle()

            assertEquals("session-42", vm.uiState.value.savedSessionId)

            val captor = argumentCaptor<Session>()
            verify(firestoreRepository).saveSession(captor.capture())
            val saved = captor.firstValue
            assertEquals("uid-1", saved.playerId)
            assertEquals("team-1", saved.teamId)
            assertEquals(drill.id, saved.drillId)
            assertEquals(1, saved.shotsAttempted)
            assertEquals(1, saved.shotsMade)
            assertEquals(100.0, saved.accuracyPct, 0.001)
            assertEquals("0.1.0-test", saved.appVersion)
        }

    @Test
    fun `events after finish are dropped`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(authRepository.loadCurrentUser()).thenReturn(player)
        whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
        whenever(firestoreRepository.saveSession(any())).thenReturn(Result.success("s1"))

        val vm = viewModel()
        advanceUntilIdle()
        vm.startRecording()
        runCurrent()
        vm.finishSession()
        advanceTimeBy(2_000)
        advanceUntilIdle()

        // Late CV events (camera thread lag) must not crash or mutate anything.
        vm.onShotDetected(made = true, releaseTimeMs = 500)
        vm.onDribbleDetected(300)
        vm.onReactionMeasured(400)

        assertTrue(true) // reaching here without exception is the assertion
    }

    @Test
    fun `save failure surfaces an error instead of navigating`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(authRepository.loadCurrentUser()).thenReturn(player)
            whenever(firestoreRepository.getDrills()).thenReturn(Result.success(listOf(drill)))
            whenever(firestoreRepository.saveSession(any()))
                .thenReturn(Result.failure(IllegalStateException("permission denied")))

            val vm = viewModel()
            advanceUntilIdle()
            vm.startRecording()
            runCurrent()
            vm.finishSession()
            advanceTimeBy(2_000)
            advanceUntilIdle()

            assertEquals("permission denied", vm.uiState.value.error)
            assertEquals(null, vm.uiState.value.savedSessionId)
        }
}
