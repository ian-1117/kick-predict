package com.kickpredict.presentation.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.R
import com.kickpredict.domain.usecase.TeamRef
import com.kickpredict.presentation.components.PowerComparisonReport
import com.kickpredict.presentation.components.PredictionDonutChart
import com.kickpredict.presentation.components.ProbabilityGauges
import com.kickpredict.presentation.theme.AccentPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.leagues.size > 1) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.leagues.forEach { league ->
                                    FilterChip(
                                        selected = state.selectedLeague == league,
                                        onClick = { viewModel.selectLeague(league) },
                                        label = { Text(league.displayName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentPrimary.copy(alpha = 0.22f),
                                            selectedLabelColor = AccentPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TeamSelector(state.teamA, state.teams, viewModel::selectA, Modifier.weight(1f))
                            TeamSelector(state.teamB, state.teams, viewModel::selectB, Modifier.weight(1f))
                        }
                    }
                    val matchup = state.matchup
                    val prediction = matchup?.predictedResult
                    if (matchup != null && prediction != null) {
                        item {
                            PredictionDonutChart(prediction, modifier = Modifier.fillMaxWidth(0.62f))
                        }
                        item {
                            ProbabilityGauges(prediction, matchup.homeTeam.shortName, matchup.awayTeam.shortName)
                        }
                        item {
                            PowerComparisonReport(
                                home = matchup.homeTeam,
                                away = matchup.awayTeam,
                                rationale = prediction.rationale,
                                headToHead = matchup.headToHead,
                                matchupEdge = prediction.matchupEdge,
                                matchupStrengthPercent = prediction.matchupStrengthPercent,
                                context = matchup.context,
                            )
                        }
                    } else {
                        item {
                            Text(
                                stringResource(R.string.compare_no_matchup),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSelector(selected: TeamRef?, options: List<TeamRef>, onSelect: (TeamRef) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected?.name ?: "-",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = AccentPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team.name) },
                    onClick = { onSelect(team); expanded = false },
                )
            }
        }
    }
}
