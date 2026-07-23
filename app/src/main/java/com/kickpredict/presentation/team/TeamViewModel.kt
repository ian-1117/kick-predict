package com.kickpredict.presentation.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kickpredict.KickPredictApplication
import com.kickpredict.domain.usecase.GetTeamUseCase
import com.kickpredict.domain.usecase.TeamDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamUiState(
    val isLoading: Boolean = true,
    val team: TeamDetail? = null,
    val error: String? = null,
)

class TeamViewModel(
    private val teamId: String,
    private val getTeam: GetTeamUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamUiState())
    val uiState: StateFlow<TeamUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { getTeam(teamId) }
                .onSuccess { _uiState.value = TeamUiState(isLoading = false, team = it, error = if (it == null) "Team not found" else null) }
                .onFailure { _uiState.value = TeamUiState(isLoading = false, error = it.message) }
        }
    }

    companion object {
        fun provideFactory(teamId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KickPredictApplication
                TeamViewModel(teamId, app.container.getTeam)
            }
        }
    }
}
