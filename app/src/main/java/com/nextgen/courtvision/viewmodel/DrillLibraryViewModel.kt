package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrillLibraryUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val drills: List<Drill> = emptyList(),
    val joiningTeam: Boolean = false,
    val error: String? = null,
) {
    val needsTeam: Boolean get() = user != null && user.teamId == null
}

class DrillLibraryViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrillLibraryUiState())
    val uiState: StateFlow<DrillLibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val user = authRepository.loadCurrentUser()
            firestoreRepository.getDrills()
                .onSuccess { drills ->
                    _uiState.update { it.copy(loading = false, user = user, drills = drills) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, user = user, error = e.message ?: "Could not load drills")
                    }
                }
        }
    }

    fun joinTeam(code: String) {
        val playerId = _uiState.value.user?.uid ?: return
        _uiState.update { it.copy(joiningTeam = true, error = null) }
        viewModelScope.launch {
            firestoreRepository.joinTeam(code, playerId)
                .onSuccess {
                    // Re-fetch the profile so the banner disappears with fresh state.
                    val user = authRepository.loadCurrentUser()
                    _uiState.update { it.copy(joiningTeam = false, user = user) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(joiningTeam = false, error = e.message ?: "Could not join team")
                    }
                }
        }
    }

    fun acknowledgeError() {
        _uiState.update { it.copy(error = null) }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val firestoreRepository: FirestoreRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DrillLibraryViewModel(authRepository, firestoreRepository) as T
    }
}
