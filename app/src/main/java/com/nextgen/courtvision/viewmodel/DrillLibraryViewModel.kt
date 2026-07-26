package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.User
import com.nextgen.courtvision.domain.stats.PlayerStats
import com.nextgen.courtvision.domain.stats.StatsCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrillLibraryUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val drills: List<Drill> = emptyList(),
    val stats: PlayerStats = PlayerStats.EMPTY,
    val insights: List<String> = emptyList(),
    val joiningTeam: Boolean = false,
    val error: String? = null,
) {
    val needsTeam: Boolean get() = user != null && user.teamId == null
}

/** Player home: drill catalogue + live training stats derived from history. */
class DrillLibraryViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrillLibraryUiState())
    val uiState: StateFlow<DrillLibraryUiState> = _uiState.asStateFlow()

    private var statsJob: Job? = null

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
            if (user != null) observeStats(user.uid)
        }
    }

    private fun observeStats(playerId: String) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            firestoreRepository.observeSessions(playerId)
                .catch { /* stats are additive — home stays usable without them */ }
                .collect { sessions ->
                    val stats = StatsCalculator.compute(sessions, clock())
                    _uiState.update {
                        it.copy(stats = stats, insights = StatsCalculator.insights(stats))
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
