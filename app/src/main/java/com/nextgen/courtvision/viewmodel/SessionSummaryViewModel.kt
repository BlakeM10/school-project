package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionSummaryUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val drill: Drill? = null,
    val error: String? = null,
)

class SessionSummaryViewModel(
    private val sessionId: String,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionSummaryUiState())
    val uiState: StateFlow<SessionSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            firestoreRepository.getSession(sessionId)
                .onSuccess { session ->
                    val drill = firestoreRepository.getDrills()
                        .getOrNull()?.firstOrNull { it.id == session.drillId }
                    _uiState.update { it.copy(loading = false, session = session, drill = drill) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Could not load session") }
                }
        }
    }

    class Factory(
        private val sessionId: String,
        private val firestoreRepository: FirestoreRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionSummaryViewModel(sessionId, firestoreRepository) as T
    }
}
