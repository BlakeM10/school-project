package com.nextgen.courtvision.viewmodel

import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.GoogleSignInResult
import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    private val player = Player("uid-1", "Martin", "martin@example.com")
    private val coach = Coach("uid-2", "Coach Banda", "banda@example.com")

    @Before
    fun setUp() {
        repository = mock()
        viewModel = AuthViewModel(repository)
    }

    @Test
    fun `successful email sign-in emits Authenticated`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(repository.signInWithEmail(any(), any())).thenReturn(Result.success(player))

        viewModel.signIn("martin@example.com", "secret123")
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(player), viewModel.uiState.value)
    }

    @Test
    fun `failed email sign-in emits Error with repository message`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(repository.signInWithEmail(any(), any()))
                .thenReturn(Result.failure(IllegalStateException("Wrong password")))

            viewModel.signIn("martin@example.com", "bad-password")
            advanceUntilIdle()

            assertEquals(AuthUiState.Error("Wrong password"), viewModel.uiState.value)
        }

    @Test
    fun `invalid email is rejected before calling the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.signIn("not-an-email", "secret123")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AuthUiState.Error)
            verify(repository, never()).signInWithEmail(any(), any())
        }

    @Test
    fun `registration without a role is rejected before calling the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.register("Martin", "martin@example.com", "secret123", role = null)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AuthUiState.Error)
            verify(repository, never()).registerWithEmail(any(), any(), any(), any())
        }

    @Test
    fun `successful registration emits Authenticated`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(repository.registerWithEmail(any(), any(), any(), any()))
            .thenReturn(Result.success(coach))

        viewModel.register("Coach Banda", "banda@example.com", "secret123", Role.COACH)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(coach), viewModel.uiState.value)
    }

    @Test
    fun `first-time google sign-in emits NeedsProfile`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(repository.signInWithGoogle(any()))
            .thenReturn(Result.success(GoogleSignInResult.ProfileMissing("Martin", "m@example.com")))

        viewModel.signInWithGoogle("fake-id-token")
        advanceUntilIdle()

        assertEquals(AuthUiState.NeedsProfile("Martin"), viewModel.uiState.value)
    }

    @Test
    fun `returning google sign-in emits Authenticated`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(repository.signInWithGoogle(any()))
            .thenReturn(Result.success(GoogleSignInResult.Success(player)))

        viewModel.signInWithGoogle("fake-id-token")
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(player), viewModel.uiState.value)
    }

    @Test
    fun `sign out clears state and delegates to repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.signOut()

            assertEquals(AuthUiState.Idle, viewModel.uiState.value)
            verify(repository).signOut()
        }

    @Test
    fun `restoreSession emits Authenticated when a session exists`() =
        runTest(mainDispatcherRule.dispatcher) {
            whenever(repository.loadCurrentUser()).thenReturn(player)

            viewModel.restoreSession()
            advanceUntilIdle()

            assertEquals(AuthUiState.Authenticated(player), viewModel.uiState.value)
        }
}
