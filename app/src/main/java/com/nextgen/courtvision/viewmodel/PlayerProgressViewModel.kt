package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProgressUiState(
    val loading: Boolean = true,
    val sessions: List<Session> = emptyList(),
    val drillsById: Map<String, Drill> = emptyMap(),
    val error: String? = null,
)

/** Longitudinal progress for the signed-in player (live-updating). */
class PlayerProgressViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerProgressUiState())
    val uiState: StateFlow<PlayerProgressUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.loadCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(loading = false, error = "Not signed in") }
                return@launch
            }
            val drills = firestoreRepository.getDrills().getOrNull().orEmpty()
            _uiState.update { it.copy(drillsById = drills.associateBy { d -> d.id }) }

            firestoreRepository.observeSessions(user.uid)
                .catch { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Could not load history") }
                }
                .collect { sessions ->
                    _uiState.update { it.copy(loading = false, sessions = sessions) }
                }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteSession(sessionId)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Could not delete session") }
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
            PlayerProgressViewModel(authRepository, firestoreRepository) as T
    }
}
