package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Drill
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.stats.PlayerStats
import com.nextgen.courtvision.domain.stats.StatsCalculator
import com.nextgen.courtvision.domain.stats.StatsPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProgressUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val allSessions: List<Session> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val stats: PlayerStats = PlayerStats.EMPTY,
    val drillsById: Map<String, Drill> = emptyMap(),
    val error: String? = null,
)

/** Longitudinal analytics with period filtering — live-updating. */
class PlayerProgressViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val clock: () -> Long = System::currentTimeMillis,
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
                    _uiState.update { state ->
                        applyPeriod(state.copy(loading = false, allSessions = sessions), state.period)
                    }
                }
        }
    }

    fun setPeriod(period: StatsPeriod) {
        _uiState.update { applyPeriod(it, period) }
    }

    private fun applyPeriod(state: PlayerProgressUiState, period: StatsPeriod): PlayerProgressUiState {
        val filtered = StatsCalculator.filterByPeriod(state.allSessions, period, clock())
        return state.copy(
            period = period,
            sessions = filtered,
            stats = StatsCalculator.compute(filtered, clock()),
        )
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
