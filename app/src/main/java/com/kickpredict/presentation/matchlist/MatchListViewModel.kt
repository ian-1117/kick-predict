package com.kickpredict.presentation.matchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.usecase.CalibrationStatus
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How the fixtures list is grouped into sections. */
enum class GroupMode(val label: String) { ROUND("라운드별"), WEEK("주별") }

data class MatchSection(val title: String, val matches: List<Match>)

data class MatchListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val leagueFilter: LeagueType? = null, // null == all leagues
    val groupMode: GroupMode = GroupMode.ROUND,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sections: List<MatchSection> = emptyList(),
    val totalCount: Int = 0,
    val calibration: CalibrationStatus? = null,
)

class MatchListViewModel(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
    private val recalibrate: RecalibrateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchListUiState())
    val uiState: StateFlow<MatchListUiState> = _uiState.asStateFlow()

    private var allMatches: List<Match> = emptyList()
    private var loadedOnce = false

    /**
     * Called on every screen resume. The first time shows the loading spinner; on return from the
     * detail screen it refreshes silently so newly entered results (and any recalibration they
     * triggered) are reflected without a jarring full reload.
     */
    fun onResume() {
        load(silent = loadedOnce)
    }

    /** Public reload (e.g. retry after an error) — always shows the spinner. */
    fun load() = load(silent = false)

    private fun load(silent: Boolean) {
        if (!silent) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // Refit calibration from accumulated results first, so these predictions use it.
            val status = runCatching { recalibrate() }.getOrNull()
            runCatching { getPredictedMatches() }
                .onSuccess { matches ->
                    allMatches = matches
                    loadedOnce = true
                    _uiState.value = _uiState.value.copy(isLoading = false, calibration = status)
                    rebuild()
                }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = t.message)
                }
        }
    }

    fun setLeague(league: LeagueType?) {
        _uiState.value = _uiState.value.copy(leagueFilter = league)
        rebuild()
    }

    fun setGroupMode(mode: GroupMode) {
        _uiState.value = _uiState.value.copy(groupMode = mode)
        rebuild()
    }

    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        _uiState.value = _uiState.value.copy(fromDate = from, toDate = to)
        rebuild()
    }

    fun clearDateRange() = setDateRange(null, null)

    private fun rebuild() {
        val state = _uiState.value
        val filtered = allMatches
            .filter { state.leagueFilter == null || it.league == state.leagueFilter }
            .filter { m ->
                val date = m.kickoff.toLocalDate()
                (state.fromDate == null || !date.isBefore(state.fromDate)) &&
                    (state.toDate == null || !date.isAfter(state.toDate))
            }
            .sortedBy { it.kickoff }

        val sections = when (state.groupMode) {
            GroupMode.ROUND -> filtered
                .groupBy { it.round }
                .toSortedMap()
                .map { (round, ms) -> MatchSection("라운드 $round", ms.sortedBy { it.kickoff }) }
            GroupMode.WEEK -> filtered
                .groupBy { it.kickoff.toLocalDate().with(DayOfWeek.MONDAY) }
                .toSortedMap()
                .map { (weekStart, ms) ->
                    MatchSection(weekTitle(weekStart), ms.sortedBy { it.kickoff })
                }
        }
        _uiState.value = state.copy(sections = sections, totalCount = filtered.size)
    }

    private fun weekTitle(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("M.d")
        return "${weekStart.format(fmt)} – ${end.format(fmt)} 주"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                MatchListViewModel(app.container.getPredictedMatches, app.container.recalibrate)
            }
        }
    }
}
