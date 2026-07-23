package com.kickpredict.presentation.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.usecase.CompareTeamsUseCase
import com.kickpredict.domain.usecase.TeamRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompareUiState(
    val isLoading: Boolean = true,
    val leagues: List<LeagueType> = emptyList(),
    val selectedLeague: LeagueType? = null,
    val teams: List<TeamRef> = emptyList(),
    val teamA: TeamRef? = null,
    val teamB: TeamRef? = null,
    val matchup: Match? = null,
)

class CompareViewModel(private val compare: CompareTeamsUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private var byLeague: Map<LeagueType, List<TeamRef>> = emptyMap()

    init {
        viewModelScope.launch {
            byLeague = runCatching { compare.teamsByLeague() }.getOrDefault(emptyMap())
            val firstLeague = byLeague.keys.firstOrNull()
            _uiState.value = _uiState.value.copy(isLoading = false, leagues = byLeague.keys.toList())
            firstLeague?.let { selectLeague(it) }
        }
    }

    fun selectLeague(league: LeagueType) {
        val teams = byLeague[league].orEmpty()
        _uiState.value = _uiState.value.copy(
            selectedLeague = league,
            teams = teams,
            teamA = teams.getOrNull(0),
            teamB = teams.getOrNull(1),
        )
        refreshMatchup()
    }

    fun selectA(team: TeamRef) {
        _uiState.value = _uiState.value.copy(teamA = team)
        refreshMatchup()
    }

    fun selectB(team: TeamRef) {
        _uiState.value = _uiState.value.copy(teamB = team)
        refreshMatchup()
    }

    private fun refreshMatchup() {
        val a = _uiState.value.teamA ?: return
        val b = _uiState.value.teamB ?: return
        viewModelScope.launch {
            val matchup = runCatching { compare.matchup(a.id, b.id) }.getOrNull()
            _uiState.value = _uiState.value.copy(matchup = matchup)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                CompareViewModel(app.container.compareTeams)
            }
        }
    }
}
