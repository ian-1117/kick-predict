package com.kickpredict.presentation.simulation

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
import com.kickpredict.presentation.theme.LimeGreen
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineGrey
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
                title = { Text("시즌 시뮬레이션", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(color = LimeGreen, modifier = Modifier.align(Alignment.Center))

                state.error != null -> Text(
                    state.error!!,
                    color = LossColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                state.selected == null -> Text(
                    "시뮬레이션할 일정이 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    LeagueChips(state, viewModel)
                    CutoffCard(state, viewModel)
                    MetricChips(state, viewModel)
                    ProjectionList(state)
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
                    selectedContainerColor = LimeGreen.copy(alpha = 0.22f),
                    selectedLabelColor = LimeGreen,
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
                "시뮬레이션 시작",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${state.cutoffRound}라운드",
                style = MaterialTheme.typography.titleMedium,
                color = LimeGreen,
                fontWeight = FontWeight.Black,
            )
        }

        Slider(
            value = state.cutoffRound.toFloat(),
            onValueChange = { viewModel.setCutoff(it.toInt()) },
            valueRange = 1f..state.lastRound.toFloat().coerceAtLeast(2f),
            steps = (state.lastRound - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = LimeGreen,
                activeTrackColor = LimeGreen,
                inactiveTrackColor = OutlineGrey,
            ),
        )

        val played = state.playedRounds
        val summary = if (played <= 0) {
            "프리시즌 — 전 경기 시뮬레이션"
        } else {
            "1~${played}R 실제 결과 · ${state.cutoffRound}~${state.lastRound}R 시뮬레이션"
        }
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (projection != null) {
            Text(
                "${"%,d".format(projection.iterations)}회 반복 · 잔여 ${projection.remainingMatches}경기",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isSimulating) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = LimeGreen,
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
                label = { Text("${metric.label} 확률") },
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
                    "‘실제’는 이 시즌이 실제로 끝난 순위입니다. 컷오프 이후 경기는 그 시점까지 " +
                        "알 수 있었던 기록만으로 예측하므로, 모델은 자신이 맞혀야 할 결과를 미리 보지 않습니다. " +
                        "다만 표본은 한 시즌뿐입니다 — 몇 개 맞혔다고 예측력이 검증된 것은 아닙니다.",
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
                "예상 승점 ${"%.1f".format(team.expectedPoints)} · 평균 ${"%.1f".format(team.averageRank)}위",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only a team that really did win / qualify / go down gets the accent; the rest stay muted
            // so the outcome column can't be misread as the model having called every row.
            team.actualOutcome(metric, projection)?.let { (label, hit) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hit) accent.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hit) FontWeight.Bold else FontWeight.Normal,
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
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(OutlineGrey.copy(alpha = 0.5f)),
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
private fun TeamProjection.actualOutcome(metric: ProjectionMetric, projection: SeasonProjection): Pair<String, Boolean>? {
    val actual = actualRank ?: return null
    val teamCount = projection.teams.size
    val hit = when (metric) {
        ProjectionMetric.TITLE -> actual == 1
        ProjectionMetric.CONTINENTAL -> actual <= projection.rules.continentalSpots
        ProjectionMetric.RELEGATION -> actual > teamCount - projection.rules.relegationSpots
    }
    return if (hit) "실제: ${metric.label} ✓" to true else "실제 ${actual}위" to false
}

@Composable
@ReadOnlyComposable
private fun ProjectionMetric.accent(): Color = when (this) {
    ProjectionMetric.TITLE -> LimeGreen
    ProjectionMetric.CONTINENTAL -> DrawColor
    ProjectionMetric.RELEGATION -> LossColor
}
