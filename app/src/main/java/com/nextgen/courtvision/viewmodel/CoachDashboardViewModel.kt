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
    val creatingTeam: Boolean = false,
    val error: String? = null,
) {
    val needsTeam: Boolean get() = user != null && user.teamId == null
}

class CoachDashboardViewModel(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachDashboardUiState())
    val uiState: StateFlow<CoachDashboardUiState> = _uiState.asStateFlow()

    private var rosterJob: Job? = null
    private var sessionsJob: Job? = null

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
        rosterJob?.cancel()
        rosterJob = viewModelScope.launch {
            firestoreRepository.observeTeamPlayers(teamId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message ?: "Could not load roster") }
                }
                .collect { players ->
                    val previousSelection = _uiState.value.selectedPlayer
                    _uiState.update { it.copy(players = players) }
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

    class Factory(
        private val authRepository: AuthRepository,
        private val firestoreRepository: FirestoreRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachDashboardViewModel(authRepository, firestoreRepository) as T
    }
}
