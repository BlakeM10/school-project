package com.nextgen.courtvision.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.Session
import com.nextgen.courtvision.domain.model.Team
import com.nextgen.courtvision.domain.model.User
import com.nextgen.courtvision.domain.stats.PlayerSummary
import com.nextgen.courtvision.domain.stats.StatsCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachDashboardUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val team: Team? = null,
    val players: List<Player> = emptyList(),
    val selectedPlayer: Player? = null,
    val selectedPlayerSessions: List<Session> = emptyList(),
    val teamSessions: List<Session> = emptyList(),
    val summaries: List<PlayerSummary> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val creatingTeam: Boolean = false,
    val error: String? = null,
) {
    val needsTeam: Boolean get() = user != null && user.teamId == null

    val teamAvgAccuracyPct: Double
        get() = summaries.filter { it.sessionCount > 0 }
            .map { it.avgAccuracyPct }.let { if (it.isEmpty()) 0.0 else it.average() }
    val improvingCount: Int get() = summaries.count { it.improving }
    val attentionCount: Int get() = summaries.count { it.needsAttention }
    val topPerformers: List<PlayerSummary> get() = summaries.filter { it.sessionCount > 0 }.take(3)
    val needsAttention: List<PlayerSummary> get() = summaries.filter { it.needsAttention }.take(3)
}

class CoachDashboardViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachDashboardUiState())
    val uiState: StateFlow<CoachDashboardUiState> = _uiState.asStateFlow()

    private var rosterJob: Job? = null
    private var sessionsJob: Job? = null
    private var teamSessionsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val user = authRepository.loadCurrentUser()
            val teamId = user?.teamId
            if (teamId == null) {
                _uiState.update { it.copy(loading = false, user = user) }
                return@launch
            }
            firestoreRepository.getTeam(teamId)
                .onSuccess { team ->
                    _uiState.update { it.copy(loading = false, user = user, team = team) }
                    observeRoster(teamId)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, user = user, error = e.message ?: "Could not load team")
                    }
                }
        }
    }

    fun createTeam(name: String) {
        val coachId = _uiState.value.user?.uid ?: return
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Enter a team name") }
            return
        }
        _uiState.update { it.copy(creatingTeam = true, error = null) }
        viewModelScope.launch {
            firestoreRepository.createTeam(name, coachId)
                .onSuccess { team ->
                    val user = authRepository.loadCurrentUser()
                    _uiState.update {
                        it.copy(creatingTeam = false, user = user, team = team)
                    }
                    observeRoster(team.id)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(creatingTeam = false, error = e.message ?: "Could not create team")
                    }
                }
        }
    }

    fun selectPlayer(player: Player) {
        _uiState.update { it.copy(selectedPlayer = player, selectedPlayerSessions = emptyList()) }
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            firestoreRepository.observeSessions(player.uid)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message ?: "Could not load sessions") }
                }
                .collect { sessions ->
                    _uiState.update { it.copy(selectedPlayerSessions = sessions) }
                }
        }
    }

    fun acknowledgeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeRoster(teamId: String) {
        observeTeamSessions(teamId)
        rosterJob?.cancel()
        rosterJob = viewModelScope.launch {
            firestoreRepository.observeTeamPlayers(teamId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message ?: "Could not load roster") }
                }
                .collect { players ->
                    val previousSelection = _uiState.value.selectedPlayer
                    _uiState.update { state ->
                        val summaries = StatsCalculator.playerSummaries(players, state.teamSessions)
                        state.copy(
                            players = players,
                            summaries = summaries,
                            recommendations = StatsCalculator.coachRecommendations(summaries),
                        )
                    }
                    // Keep the current selection if that player is still on the
                    // roster; otherwise auto-select the first player so the
                    // trend chart is never empty while data exists.
                    val retained = previousSelection?.let { sel -> players.firstOrNull { it.uid == sel.uid } }
                    when {
                        retained != null && retained.uid == previousSelection.uid && sessionsJob != null ->
                            _uiState.update { it.copy(selectedPlayer = retained) }
                        players.isNotEmpty() -> selectPlayer(retained ?: players.first())
                        else -> _uiState.update {
                            it.copy(selectedPlayer = null, selectedPlayerSessions = emptyList())
                        }
                    }
                }
        }
    }

    private fun observeTeamSessions(teamId: String) {
        teamSessionsJob?.cancel()
        teamSessionsJob = viewModelScope.launch {
            firestoreRepository.observeTeamSessions(teamId)
                .catch { /* team analytics are additive; dashboard stays usable */ }
                .collect { sessions ->
                    _uiState.update { state ->
                        val summaries = StatsCalculator.playerSummaries(state.players, sessions)
                        state.copy(
                            teamSessions = sessions,
                            summaries = summaries,
                            recommendations = StatsCalculator.coachRecommendations(summaries),
                        )
                    }
                }
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val firestoreRepository: FirestoreRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachDashboardViewModel(authRepository, firestoreRepository) as T
    }
}
