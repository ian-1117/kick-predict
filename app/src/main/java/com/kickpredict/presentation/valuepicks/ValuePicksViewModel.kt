package com.kickpredict.presentation.valuepicks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.ValuePicksReport
import com.kickpredict.domain.model.buildValuePicksReport
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.GetValuePicksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ValuePicksUiState(
    val isLoading: Boolean = true,
    val leagueFilter: LeagueType? = null, // null == all leagues
    /** Leagues that actually have picks, in league order — the filter chips to offer. */
    val leagues: List<LeagueType> = emptyList(),
    /** Rolled up for the selected league — ROI + counts recompute when the filter changes. */
    val report: ValuePicksReport = ValuePicksReport(),
)

class ValuePicksViewModel(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
    private val getValuePicks: GetValuePicksUseCase,
    private val oddsProvider: () -> Map<String, MarketOdds>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ValuePicksUiState())
    val uiState: StateFlow<ValuePicksUiState> = _uiState.asStateFlow()

    // All settled picks across leagues; the displayed report is filtered from this.
    private var allPicks: List<ValuePick> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Log any picks the model flags right now (best-effort) so the ledger stays current.
            runCatching {
                val matches = getPredictedMatches.cached() ?: getPredictedMatches()
                getValuePicks.record(matches, oddsProvider(), System.currentTimeMillis())
            }
            allPicks = runCatching { getValuePicks.report().picks }.getOrDefault(emptyList())
            val leagues = LeagueType.entries.filter { l -> allPicks.any { it.league == l } }
            _uiState.value = _uiState.value.copy(isLoading = false, leagues = leagues, report = rollup())
        }
    }

    fun setLeague(league: LeagueType?) {
        _uiState.value = _uiState.value.copy(leagueFilter = league, report = rollup(league))
    }

    private fun rollup(league: LeagueType? = _uiState.value.leagueFilter): ValuePicksReport =
        buildValuePicksReport(allPicks.filter { league == null || it.league == league })

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                ValuePicksViewModel(
                    app.container.getPredictedMatches,
                    app.container.getValuePicks,
                    app.container.odds,
                )
            }
        }
    }
}
