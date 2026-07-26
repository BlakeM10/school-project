package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.session.SessionRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveSessionUiState(
    val loading: Boolean = true,
    val drill: Drill? = null,
    val recording: Boolean = false,
    val stats: SessionRecorder.LiveStats? = null,
    val saving: Boolean = false,
    val savedSessionId: String? = null,
    val error: String? = null,
)

/**
 * Drives the recording lifecycle: load drill → start recorder → (CV events
 * update live stats) → finish → persist → hand off to the summary screen.
 * In Phase 6 the CV pipeline is not yet attached, so metrics stay at zero;
 * Phase 7 connects CVPipeline events to the same recorder methods.
 */
class LiveSessionViewModel(
    private val drillId: String,
    private val appVersion: String,
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveSessionUiState())
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private var recorder: SessionRecorder? = null

    init {
        viewModelScope.launch {
            val user = authRepository.loadCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(loading = false, error = "Not signed in") }
                return@launch
            }
            firestoreRepository.getDrills()
                .onSuccess { drills ->
                    val drill = drills.firstOrNull { it.id == drillId }
                    if (drill == null) {
                        _uiState.update { it.copy(loading = false, error = "Drill not found") }
                    } else {
                        recorder = SessionRecorder(
                            playerId = user.uid,
                            teamId = user.teamId,
                            drill = drill,
                            appVersion = appVersion,
                        )
                        _uiState.update { it.copy(loading = false, drill = drill) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Could not load drill") }
                }
        }
    }

    fun startRecording() {
        val recorder = recorder ?: return
        if (recorder.isRunning) return
        recorder.start()
        _uiState.update { it.copy(recording = true, stats = recorder.currentStats()) }
        viewModelScope.launch {
            while (_uiState.value.recording) {
                _uiState.update { it.copy(stats = recorder.currentStats()) }
                delay(1_000)
            }
        }
    }

    fun finishSession() {
        val recorder = recorder ?: return
        if (!recorder.isRunning) return
        _uiState.update { it.copy(recording = false, saving = true) }
        viewModelScope.launch {
            val session = recorder.finish()
            firestoreRepository.saveSession(session)
                .onSuccess { id ->
                    _uiState.update { it.copy(saving = false, savedSessionId = id) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(saving = false, error = e.message ?: "Could not save session") }
                }
        }
    }

    fun acknowledgeError() {
        _uiState.update { it.copy(error = null) }
    }

    class Factory(
        private val drillId: String,
        private val appVersion: String,
        private val authRepository: AuthRepository,
        private val firestoreRepository: FirestoreRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LiveSessionViewModel(drillId, appVersion, authRepository, firestoreRepository) as T
    }
}
