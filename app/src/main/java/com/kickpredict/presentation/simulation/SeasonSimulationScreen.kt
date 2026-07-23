package com.kickpredict.presentation.simulation

import androidx.compose.ui.res.stringResource
import com.kickpredict.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.simulation.SeasonProjection
import com.kickpredict.domain.simulation.TeamProjection
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.Surface1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonSimulationScreen(
    onBack: () -> Unit,
    viewModel: SeasonSimulationViewModel = viewModel(factory = SeasonSimulationViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.standings_simulate), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))

                state.error != null -> Text(
                    state.error!!,
                    color = LossColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                state.selected == null -> Text(
                    stringResource(R.string.sim_no_fixtures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    LeagueChips(state, viewModel)
                    CutoffCard(state, viewModel)
                    MetricChips(state, viewModel)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (state.projection != null) ProjectionList(state)
                        // Spinner only while there's nothing to show (league switch / first run) — a
                        // cutoff drag keeps the current league's list up to avoid flicker.
                        if (state.isSimulating && state.projection == null) {
                            CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueChips(state: SeasonSimulationUiState, viewModel: SeasonSimulationViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.leagues.forEach { league ->
            FilterChip(
                selected = state.selected == league,
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

@Composable
private fun CutoffCard(state: SeasonSimulationUiState, viewModel: SeasonSimulationViewModel) {
    val projection = state.projection
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sim_start),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.sim_round, state.cutoffRound),
                style = MaterialTheme.typography.titleMedium,
                color = AccentPrimary,
                fontWeight = FontWeight.Black,
            )
        }

        Slider(
            value = state.cutoffRound.toFloat(),
            onValueChange = { viewModel.setCutoff(it.toInt()) },
            valueRange = 1f..state.lastRound.toFloat().coerceAtLeast(2f),
            steps = (state.lastRound - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = OutlineColor,
            ),
        )

        val played = state.playedRounds
        val summary = if (played <= 0) {
            stringResource(R.string.sim_preseason)
        } else {
            stringResource(R.string.sim_split, played, state.cutoffRound, state.lastRound)
        }
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (projection != null) {
            Text(
                stringResource(R.string.sim_iterations, "%,d".format(projection.iterations), projection.remainingMatches),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isSimulating) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = AccentPrimary,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun MetricChips(state: SeasonSimulationUiState, viewModel: SeasonSimulationViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.availableMetrics.forEach { metric ->
            val accent = metric.accent()
            FilterChip(
                selected = state.metric == metric,
                onClick = { viewModel.selectMetric(metric) },
                label = { Text(stringResource(R.string.sim_metric_prob, stringResource(metric.labelRes))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.22f),
                    selectedLabelColor = accent,
                ),
            )
        }
    }
}

@Composable
private fun ProjectionList(state: SeasonSimulationUiState) {
    val projection = state.projection ?: return
    val listState = rememberLazyListState()

    // Reordering keyed items makes LazyColumn hold the previously-first team on screen, which hides
    // the new leader above the fold. Reset once the reordering has actually landed — keying this on
    // the cutoff instead would fire while the slider moves and lose to the projection arriving later.
    LaunchedEffect(projection, state.metric) {
        listState.scrollToItem(0)
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        items(state.teams, key = { it.teamId }) { team ->
            TeamRow(team, state.metric, projection)
        }
        if (projection.hasActualOutcome) {
            item {
                Text(
                    stringResource(R.string.sim_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamProjection, metric: ProjectionMetric, projection: SeasonProjection) {
    val accent = metric.accent()
    val percent = when (metric) {
        ProjectionMetric.TITLE -> team.titlePercent
        ProjectionMetric.CONTINENTAL -> team.continentalPercent
        ProjectionMetric.RELEGATION -> team.relegationPercent
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            // Before a ball is kicked nobody has a table position; showing one would invent an order.
            Text(
                if (projection.playedMatches == 0) "–" else "${team.currentRank}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(team.teamName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(
                stringResource(R.string.sim_expected_points, "%.1f".format(team.expectedPoints), "%.1f".format(team.averageRank)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only a team that really did win / qualify / go down gets the accent; the rest stay muted
            // so the outcome column can't be misread as the model having called every row.
            team.actualOutcome(metric, projection)?.let { outcome ->
                val label = if (outcome.hit) stringResource(R.string.sim_actual_metric, stringResource(metric.labelRes))
                    else stringResource(R.string.sim_actual_rank, outcome.actualRank)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outcome.hit) accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (outcome.hit) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text(
                "$percent%",
                style = MaterialTheme.typography.titleMedium,
                color = if (percent > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(4.dp))
            ProbabilityBar(fraction = percent / 100f, accent = accent)
        }
    }
}

@Composable
private fun ProbabilityBar(fraction: Float, accent: Color) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(OutlineColor.copy(alpha = 0.5f)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
        }
    }
}

/** How the season really ended for this team, and whether that matches the metric being shown. */
private data class ActualOutcome(val hit: Boolean, val actualRank: Int)

private fun TeamProjection.actualOutcome(metric: ProjectionMetric, projection: SeasonProjection): ActualOutcome? {
    val actual = actualRank ?: return null
    val teamCount = projection.teams.size
    val hit = when (metric) {
        ProjectionMetric.TITLE -> actual == 1
        ProjectionMetric.CONTINENTAL -> actual <= projection.rules.continentalSpots
        ProjectionMetric.RELEGATION -> actual > teamCount - projection.rules.relegationSpots
    }
    return ActualOutcome(hit, actual)
}

@Composable
@ReadOnlyComposable
private fun ProjectionMetric.accent(): Color = when (this) {
    ProjectionMetric.TITLE -> AccentPrimary
    ProjectionMetric.CONTINENTAL -> DrawColor
    ProjectionMetric.RELEGATION -> LossColor
}
