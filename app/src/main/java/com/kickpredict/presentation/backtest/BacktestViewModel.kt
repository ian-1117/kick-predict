package com.kickpredict.presentation.backtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.model.BacktestReport
import com.kickpredict.domain.usecase.BacktestUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BacktestUiState(
    val isLoading: Boolean = true,
    val report: BacktestReport? = null,
)

class BacktestViewModel(private val backtest: BacktestUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val report = runCatching { backtest() }.getOrNull()
            _uiState.value = BacktestUiState(isLoading = false, report = report)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                BacktestViewModel(app.container.backtest)
            }
        }
    }
}
