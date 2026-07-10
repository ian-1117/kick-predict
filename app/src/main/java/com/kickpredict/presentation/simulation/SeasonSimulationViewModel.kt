package com.kickpredict.presentation.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.simulation.SeasonProjection
import com.kickpredict.domain.usecase.SeasonData
import com.kickpredict.domain.usecase.SimulateSeasonUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which probability the team rows rank and bar themselves by. */
enum class ProjectionMetric(val label: String) {
    TITLE("우승"),
    CONTINENTAL("상위권"),
    RELEGATION("강등"),
}

data class SeasonSimulationUiState(
    val isLoading: Boolean = true,
    val isSimulating: Boolean = false,
    val leagues: List<LeagueType> = emptyList(),
    val selected: LeagueType? = null,
    val cutoffRound: Int = 1,
    val lastRound: Int = 1,
    val metric: ProjectionMetric = ProjectionMetric.TITLE,
    val projection: SeasonProjection? = null,
    val error: String? = null,
) {
    /** Rounds whose real results feed the starting table. */
    val playedRounds: Int get() = cutoffRound - 1

    val teams: List<com.kickpredict.domain.simulation.TeamProjection>
        get() {
            val teams = projection?.teams.orEmpty()
            return when (metric) {
                ProjectionMetric.TITLE -> teams
                ProjectionMetric.CONTINENTAL -> teams.sortedByDescending { it.continentalProbability }
                ProjectionMetric.RELEGATION -> teams.sortedByDescending { it.relegationProbability }
            }
        }

    /** Relegation is meaningless in a league with nothing below it. */
    val availableMetrics: List<ProjectionMetric>
        get() = ProjectionMetric.entries.filter {
            it != ProjectionMetric.RELEGATION || (projection?.rules?.relegationSpots ?: 0) > 0
        }
}

@OptIn(FlowPreview::class)
class SeasonSimulationViewModel(
    private val simulateSeason: SimulateSeasonUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeasonSimulationUiState())
    val uiState: StateFlow<SeasonSimulationUiState> = _uiState.asStateFlow()

    private var data: SeasonData? = null

    /** Latest (league, cutoff) the user has settled on; debounced so a slider drag runs one sim. */
    private val pending = MutableStateFlow<Pair<LeagueType, Int>?>(null)

    init {
        viewModelScope.launch {
            pending.filterNotNull().debounce(SIMULATION_DEBOUNCE_MS).collectLatest { (league, cutoff) ->
                project(league, cutoff)
            }
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { simulateSeason.load() }
                .onSuccess { loaded ->
                    data = loaded
                    val league = loaded.leagues.firstOrNull()
                    if (league == null) {
                        _uiState.value = SeasonSimulationUiState(isLoading = false)
                        return@onSuccess
                    }
                    val lastRound = loaded.lastRound(league)
                    val cutoff = defaultCutoff(lastRound)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        leagues = loaded.leagues,
                        selected = league,
                        lastRound = lastRound,
                        cutoffRound = cutoff,
                    )
                    project(league, cutoff)
                }
                .onFailure { _uiState.value = SeasonSimulationUiState(isLoading = false, error = it.message) }
        }
    }

    fun selectLeague(league: LeagueType) {
        val loaded = data ?: return
        val lastRound = loaded.lastRound(league)
        val cutoff = defaultCutoff(lastRound)
        _uiState.value = _uiState.value.copy(selected = league, lastRound = lastRound, cutoffRound = cutoff)
        pending.value = league to cutoff
    }

    fun selectMetric(metric: ProjectionMetric) {
        _uiState.value = _uiState.value.copy(metric = metric)
    }

    fun setCutoff(round: Int) {
        val state = _uiState.value
        val league = state.selected ?: return
        val clamped = round.coerceIn(1, state.lastRound)
        if (clamped == state.cutoffRound) return
        _uiState.value = state.copy(cutoffRound = clamped, isSimulating = true)
        pending.value = league to clamped
    }

    private suspend fun project(league: LeagueType, cutoff: Int) {
        val loaded = data ?: return
        _uiState.value = _uiState.value.copy(isSimulating = true)
        val result = runCatching {
            withContext(Dispatchers.Default) { simulateSeason.project(loaded, league, cutoff) }
        }
        _uiState.value = result.fold(
            onSuccess = { _uiState.value.copy(isSimulating = false, projection = it, error = null) },
            onFailure = { _uiState.value.copy(isSimulating = false, error = it.message) },
        )
    }

    /** Open on the half-way point: enough real results to anchor the table, enough left to be uncertain. */
    private fun defaultCutoff(lastRound: Int): Int = (lastRound / 2).coerceAtLeast(1)

    companion object {
        private const val SIMULATION_DEBOUNCE_MS = 150L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                SeasonSimulationViewModel(app.container.simulateSeason)
            }
        }
    }
}
