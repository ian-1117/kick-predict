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
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.usecase.CalibrationStatus
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import com.kickpredict.domain.usecase.SyncResultsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** How the fixtures list is grouped into sections. Display labels are resolved in the UI layer. */
enum class GroupMode { ROUND, WEEK }

/** A section's heading, kept structured so the UI can localise it (and re-localise on language change). */
sealed interface SectionTitle {
    data class Round(val number: Int) : SectionTitle
    data class Week(val start: LocalDate, val end: LocalDate) : SectionTitle
}

/** True if either team's Korean name, English name or short code contains the query. */
private fun Match.matchesTeamQuery(query: String): Boolean =
    listOf(
        homeTeam.name, homeTeam.koreanName, homeTeam.shortName,
        awayTeam.name, awayTeam.koreanName, awayTeam.shortName,
    ).any { it.contains(query, ignoreCase = true) }

data class MatchSection(val title: SectionTitle, val matches: List<Match>)

data class MatchListUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val leagueFilter: LeagueType? = null, // null == all leagues
    val searchQuery: String = "",
    val groupMode: GroupMode = GroupMode.ROUND,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sections: List<MatchSection> = emptyList(),
    val totalCount: Int = 0,
    val calibration: CalibrationStatus? = null,
    // Kickoff span of the bundled fixtures, so the date picker opens where data actually exists
    // rather than on today's (empty, off-season/future) month.
    val earliestDate: LocalDate? = null,
    val latestDate: LocalDate? = null,
    // Actual results for already-played fixtures, keyed by match id — lets a card show the final
    // score and whether the prediction hit.
    val results: Map<String, RecordedResult> = emptyMap(),
)

class MatchListViewModel(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
    private val recalibrate: RecalibrateUseCase,
    private val calibrationRepository: CalibrationRepository,
    private val syncResults: SyncResultsUseCase,
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

    /** Pull-to-refresh: force a fresh network fetch while keeping the current list on screen. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        viewModelScope.launch {
            runCatching { getPredictedMatches(forceRefresh = true) }
                .onSuccess { applyMatches(it) }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    private fun load(silent: Boolean) {
        viewModelScope.launch {
            // Instant first paint from the persisted cache, so a cold start isn't a blank spinner.
            if (!loadedOnce) {
                val cachedMatches = runCatching { getPredictedMatches.cached() }.getOrNull()
                if (!cachedMatches.isNullOrEmpty()) applyMatches(cachedMatches)
                else if (!silent) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            // Fresh data from the network.
            runCatching { getPredictedMatches() }
                .onSuccess { applyMatches(it) }
                .onFailure { t ->
                    if (allMatches.isEmpty()) _uiState.value = _uiState.value.copy(isLoading = false, error = t.message)
                }
        }
    }

    /** Fold a loaded fixture set into state: default the range on first load, seed results, refit. */
    private suspend fun applyMatches(matches: List<Match>) {
        val isFirstLoad = !loadedOnce
        allMatches = matches
        loadedOnce = true
        val dates = matches.map { it.kickoff.toLocalDate() }
        val earliest = dates.minOrNull()
        val latest = dates.maxOrNull()
        // Open on the current round: on the first load, default the range to "today → end of the
        // loaded fixtures" so already-played rounds are hidden and the list starts on what's coming.
        val today = LocalDate.now()
        val current = _uiState.value
        val (from, to) = if (isFirstLoad && current.fromDate == null &&
            latest != null && !latest.isBefore(today)
        ) {
            maxOf(today, earliest ?: today) to latest
        } else {
            current.fromDate to current.toDate
        }
        runCatching { syncResults() }
        val status = runCatching { recalibrate() }.getOrNull()
        val results = runCatching { calibrationRepository.recordedResults().associateBy { it.matchId } }
            .getOrDefault(emptyMap())
        _uiState.value = current.copy(
            isLoading = false,
            calibration = status,
            earliestDate = earliest,
            latestDate = latest,
            fromDate = from,
            toDate = to,
            results = results,
        )
        rebuild()
    }

    fun setLeague(league: LeagueType?) {
        _uiState.value = _uiState.value.copy(leagueFilter = league)
        rebuild()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
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

    /** Jump back to the current round: clear the search and refocus the range on today → season end. */
    fun jumpToCurrentRound() {
        val s = _uiState.value
        val today = LocalDate.now()
        val latest = s.latestDate
        val from = if (latest != null && !latest.isBefore(today)) maxOf(today, s.earliestDate ?: today) else s.earliestDate
        _uiState.value = s.copy(searchQuery = "", fromDate = from, toDate = latest ?: s.toDate)
        rebuild()
    }

    private fun rebuild() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val filtered = allMatches
            .filter { state.leagueFilter == null || it.league == state.leagueFilter }
            .filter { m ->
                val date = m.kickoff.toLocalDate()
                (state.fromDate == null || !date.isBefore(state.fromDate)) &&
                    (state.toDate == null || !date.isAfter(state.toDate))
            }
            .filter { m -> query.isEmpty() || m.matchesTeamQuery(query) }
            .sortedBy { it.kickoff }

        val sections = when (state.groupMode) {
            GroupMode.ROUND -> filtered
                .groupBy { it.round }
                .toSortedMap()
                .map { (round, ms) -> MatchSection(SectionTitle.Round(round), ms.sortedBy { it.kickoff }) }
            GroupMode.WEEK -> filtered
                .groupBy { it.kickoff.toLocalDate().with(DayOfWeek.MONDAY) }
                .toSortedMap()
                .map { (weekStart, ms) ->
                    MatchSection(SectionTitle.Week(weekStart, weekStart.plusDays(6)), ms.sortedBy { it.kickoff })
                }
        }
        _uiState.value = state.copy(sections = sections, totalCount = filtered.size)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                MatchListViewModel(
                    app.container.getPredictedMatches,
                    app.container.recalibrate,
                    app.container.calibrationRepository,
                    app.container.syncResults,
                )
            }
        }
    }
}
