package com.kickpredict.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.usecase.GetPredictedMatchUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import com.kickpredict.domain.usecase.RecordMatchResultUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PredictionDetailUiState(
    val isLoading: Boolean = true,
    val match: Match? = null,
    val error: String? = null,
    val actualResult: ActualResult? = null,
    val recordedResultCount: Int = 0,
)

class PredictionDetailViewModel(
    private val matchId: String,
    private val getPredictedMatch: GetPredictedMatchUseCase,
    private val recordMatchResult: RecordMatchResultUseCase,
    private val recalibrate: RecalibrateUseCase,
    private val calibrationRepository: CalibrationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictionDetailUiState())
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { getPredictedMatch(matchId) }
                .onSuccess { match ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        match = match,
                        error = if (match == null) "Match not found" else null,
                    )
                    refreshResult()
                }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = t.message)
                }
        }
    }

    fun recordResult(homeGoals: Int, awayGoals: Int) {
        viewModelScope.launch {
            recordMatchResult(matchId, homeGoals, awayGoals)
            refreshResult()
            // Fold the new result into calibration so later predictions benefit.
            runCatching { recalibrate() }
        }
    }

    private suspend fun refreshResult() {
        _uiState.value = _uiState.value.copy(
            actualResult = calibrationRepository.getResult(matchId),
            recordedResultCount = calibrationRepository.recordedResultCount(),
        )
    }

    companion object {
        fun provideFactory(matchId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                PredictionDetailViewModel(
                    matchId = matchId,
                    getPredictedMatch = app.container.getPredictedMatch,
                    recordMatchResult = app.container.recordMatchResult,
                    recalibrate = app.container.recalibrate,
                    calibrationRepository = app.container.calibrationRepository,
                )
            }
        }
    }
}
