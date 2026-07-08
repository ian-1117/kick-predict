package com.kickpredict.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.usecase.CalibrationDashboard
import com.kickpredict.domain.usecase.GetCalibrationDashboardUseCase
import com.kickpredict.domain.usecase.SeedSampleResultsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: CalibrationDashboard? = null,
    val error: String? = null,
    val isSeeding: Boolean = false,
)

class DashboardViewModel(
    private val getCalibrationDashboard: GetCalibrationDashboardUseCase,
    private val seedSampleResults: SeedSampleResultsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { getCalibrationDashboard() }
                .onSuccess { _uiState.value = DashboardUiState(isLoading = false, dashboard = it) }
                .onFailure { _uiState.value = DashboardUiState(isLoading = false, error = it.message) }
        }
    }

    /** Seed simulated results for all fixtures, then reload so calibration reflects them. */
    fun seedSampleResults() {
        if (_uiState.value.isSeeding) return
        _uiState.value = _uiState.value.copy(isSeeding = true)
        viewModelScope.launch {
            runCatching { seedSampleResults.invoke() }
            _uiState.value = _uiState.value.copy(isSeeding = false)
            load()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                DashboardViewModel(app.container.getCalibrationDashboard, app.container.seedSampleResults)
            }
        }
    }
}
