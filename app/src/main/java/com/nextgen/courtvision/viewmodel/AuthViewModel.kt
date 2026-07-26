package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.GoogleSignInResult
import com.nextgen.courtvision.domain.auth.CredentialsValidator
import com.nextgen.courtvision.domain.model.Role
import com.nextgen.courtvision.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data class Authenticated(val user: User) : AuthUiState()

    /** Google account authenticated but no profile exists yet — the UI must
     *  collect a display name and role before the user can proceed. */
    data class NeedsProfile(val suggestedName: String) : AuthUiState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun restoreSession() {
        viewModelScope.launch {
            authRepository.loadCurrentUser()?.let {
                _uiState.value = AuthUiState.Authenticated(it)
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!CredentialsValidator.isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("Enter a valid email address")
            return
        }
        if (password.isEmpty()) {
            _uiState.value = AuthUiState.Error("Enter your password")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.signInWithEmail(email.trim(), password)
                .onSuccess { _uiState.value = AuthUiState.Authenticated(it) }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Sign-in failed") }
        }
    }

    fun register(displayName: String, email: String, password: String, role: Role?) {
        val validationError = validateProfileInput(displayName, role)
            ?: when {
                !CredentialsValidator.isValidEmail(email) -> "Enter a valid email address"
                !CredentialsValidator.isValidPassword(password) ->
                    "Password must be at least ${CredentialsValidator.MIN_PASSWORD_LENGTH} characters"
                else -> null
            }
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.registerWithEmail(displayName.trim(), email.trim(), password, role!!)
                .onSuccess { _uiState.value = AuthUiState.Authenticated(it) }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { result ->
                    _uiState.value = when (result) {
                        is GoogleSignInResult.Success -> AuthUiState.Authenticated(result.user)
                        is GoogleSignInResult.ProfileMissing ->
                            AuthUiState.NeedsProfile(result.suggestedName)
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Google sign-in failed") }
        }
    }

    fun completeProfile(displayName: String, role: Role?) {
        val validationError = validateProfileInput(displayName, role)
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.completeProfile(displayName.trim(), role!!)
                .onSuccess { _uiState.value = AuthUiState.Authenticated(it) }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Could not save profile") }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState.Idle
    }

    fun acknowledgeError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    private fun validateProfileInput(displayName: String, role: Role?): String? = when {
        !CredentialsValidator.isValidDisplayName(displayName) -> "Enter your name"
        role == null -> "Choose a role: player or coach"
        else -> null
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(authRepository) as T
    }
}
