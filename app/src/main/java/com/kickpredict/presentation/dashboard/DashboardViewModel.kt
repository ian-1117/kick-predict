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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboard: CalibrationDashboard? = null,
    val error: String? = null,
)

class DashboardViewModel(
    private val getCalibrationDashboard: GetCalibrationDashboardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = DashboardUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { getCalibrationDashboard() }
                .onSuccess { _uiState.value = DashboardUiState(isLoading = false, dashboard = it) }
                .onFailure { _uiState.value = DashboardUiState(isLoading = false, error = it.message) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                DashboardViewModel(app.container.getCalibrationDashboard)
            }
        }
    }
}
