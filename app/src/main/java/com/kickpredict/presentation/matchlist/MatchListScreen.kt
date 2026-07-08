package com.kickpredict.presentation.matchlist

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchupEdge
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.presentation.components.TeamCrest
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.LimeGreen
import com.kickpredict.presentation.theme.LossColor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val kickoffFormatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")
private val rangeFormatter = DateTimeFormatter.ofPattern("M.d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    onMatchClick: (String) -> Unit,
    onDashboard: () -> Unit,
    viewModel: MatchListViewModel = viewModel(factory = MatchListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    // Load on first show; silently refresh when returning from the detail screen so newly entered
    // results and any recalibration they triggered are reflected.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("KICK", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        Text("PREDICT", fontWeight = FontWeight.Black, color = LimeGreen)
                    }
                },
                actions = {
                    IconButton(onClick = onDashboard) {
                        Icon(Icons.Filled.Insights, contentDescription = "적중·보정 대시보드", tint = LimeGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(color = LimeGreen, modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    FilterBar(
                        state = state,
                        onLeague = viewModel::setLeague,
                        onGroup = viewModel::setGroupMode,
                        onOpenDatePicker = { showDatePicker = true },
                        onClearDate = viewModel::clearDateRange,
                    )
                    CalibrationStatusBar(state)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.sections.forEach { section ->
                            item(key = "h_${section.title}") { SectionHeader(section.title, section.matches.size) }
                            items(section.matches, key = { it.id }) { match ->
                                MatchCard(match) { onMatchClick(match.id) }
                            }
                        }
                        if (state.sections.isEmpty()) {
                            item { Text("해당 조건의 경기가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val from = pickerState.selectedStartDateMillis?.let { millisToDate(it) }
                    val to = pickerState.selectedEndDateMillis?.let { millisToDate(it) }
                    if (from != null) viewModel.setDateRange(from, to ?: from)
                    showDatePicker = false
                }) { Text("적용") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } },
        ) {
            DateRangePicker(state = pickerState, title = { Text("기간 선택", Modifier.padding(16.dp)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    state: MatchListUiState,
    onLeague: (LeagueType?) -> Unit,
    onGroup: (GroupMode) -> Unit,
    onOpenDatePicker: () -> Unit,
    onClearDate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // League filter row (scrollable chips).
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LeagueChipFilter("전체", state.leagueFilter == null) { onLeague(null) }
            LeagueType.entries.forEach { league ->
                LeagueChipFilter(league.displayName, state.leagueFilter == league) { onLeague(league) }
            }
        }
        // Group toggle + period.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupMode.entries.forEach { mode ->
                LeagueChipFilter(mode.label, state.groupMode == mode) { onGroup(mode) }
            }
            Box(Modifier.weight(1f))
            val hasRange = state.fromDate != null
            FilterChip(
                selected = hasRange,
                onClick = { if (hasRange) onClearDate() else onOpenDatePicker() },
                label = {
                    Text(
                        if (hasRange) "${state.fromDate!!.format(rangeFormatter)}–${state.toDate!!.format(rangeFormatter)}"
                        else "기간",
                    )
                },
                leadingIcon = {
                    Icon(if (hasRange) Icons.Filled.Close else Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LimeGreen.copy(alpha = 0.2f)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeagueChipFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LimeGreen.copy(alpha = 0.22f),
            selectedLabelColor = LimeGreen,
        ),
    )
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = LimeGreen)
    }
}

@Composable
private fun CalibrationStatusBar(state: MatchListUiState) {
    val status = state.calibration ?: return
    val applied = status.confidenceApplied || status.leaguesCalibrated.isNotEmpty()
    val color = if (applied) LimeGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val detail = when {
        applied -> "신뢰도 보정 ${if (status.confidenceApplied) "적용" else "대기"} · 리그 ${status.leaguesCalibrated.size}개 보정"
        else -> "표본 축적 중"
    }
    Text(
        text = "AI 보정 · 결과 ${status.recordedResults}건 반영 · $detail",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun MatchCard(match: Match, onClick: () -> Unit) {
    val prediction = match.predictedResult
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LeagueChip(match.league.displayName)
            Text(match.kickoff.format(kickoffFormatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(match.homeTeam.shortName, match.homeTeam.crestPrimary, match.homeTeam.crestSecondary)
            Text(
                match.homeTeam.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                match.awayTeam.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            TeamCrest(match.awayTeam.shortName, match.awayTeam.crestPrimary, match.awayTeam.crestSecondary)
        }

        if (prediction != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("예측: ${outcomeLabel(match, prediction.predictedOutcome)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prediction.matchupEdge != MatchupEdge.NONE) MatchupChip(prediction.matchupEdge == MatchupEdge.HOME)
                    ConfidencePill(prediction.confidenceScore, prediction.confidenceTier)
                }
            }
        }
    }
}

private fun outcomeLabel(match: Match, outcome: PredictedOutcome): String = when (outcome) {
    PredictedOutcome.HOME_WIN -> "${match.homeTeam.displayName} 승"
    PredictedOutcome.AWAY_WIN -> "${match.awayTeam.displayName} 승"
    PredictedOutcome.DRAW -> "무승부"
}

@Composable
private fun LeagueChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(LimeGreen.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = LimeGreen)
    }
}

@Composable
private fun MatchupChip(isHome: Boolean) {
    val color = if (isHome) LimeGreen else LossColor
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text("상성", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfidencePill(score: Int, tier: ConfidenceTier) {
    val color = when (tier) {
        ConfidenceTier.VERY_HIGH, ConfidenceTier.HIGH -> LimeGreen
        ConfidenceTier.MODERATE -> DrawColor
        ConfidenceTier.LOW, ConfidenceTier.VERY_LOW -> LossColor
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text("적중률 $score%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun millisToDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
