package com.kickpredict.presentation.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.usecase.GetStandingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StandingsUiState(
    val isLoading: Boolean = true,
    val tables: Map<LeagueType, List<Standing>> = emptyMap(),
    /** Leagues whose table is last season's final one (their current season hasn't started). */
    val previousSeasonLeagues: Set<LeagueType> = emptySet(),
    val selected: LeagueType = LeagueType.K_LEAGUE,
    val error: String? = null,
) {
    val leaguesWithData: List<LeagueType> get() = LeagueType.entries.filter { tables[it]?.isNotEmpty() == true }
    val table: List<Standing> get() = tables[selected].orEmpty()
    val selectedIsPreviousSeason: Boolean get() = selected in previousSeasonLeagues
}

class StandingsViewModel(
    private val getStandings: GetStandingsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandingsUiState())
    val uiState: StateFlow<StandingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            runCatching { getStandings() }
                .onSuccess { result ->
                    val firstWithData = LeagueType.entries.firstOrNull { result.tables[it]?.isNotEmpty() == true }
                    _uiState.value = StandingsUiState(
                        isLoading = false,
                        tables = result.tables,
                        previousSeasonLeagues = result.previousSeasonLeagues,
                        selected = firstWithData ?: LeagueType.K_LEAGUE,
                    )
                }
                .onFailure { _uiState.value = StandingsUiState(isLoading = false, error = it.message) }
        }
    }

    fun selectLeague(league: LeagueType) {
        _uiState.value = _uiState.value.copy(selected = league)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                StandingsViewModel(app.container.getStandings)
            }
        }
    }
}
