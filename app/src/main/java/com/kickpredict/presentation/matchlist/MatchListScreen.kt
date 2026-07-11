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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.AppTheme
import com.kickpredict.presentation.theme.ThemePickerDialog
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.WinColor
import com.kickpredict.presentation.locale.AppLanguage
import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val kickoffFormatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")
private val rangeFormatter = DateTimeFormatter.ofPattern("M.d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    onMatchClick: (String) -> Unit,
    onDashboard: () -> Unit,
    onStandings: () -> Unit,
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    viewModel: MatchListViewModel = viewModel(factory = MatchListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    if (showThemePicker) {
        ThemePickerDialog(
            current = currentTheme,
            onSelect = onSelectTheme,
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage,
            onDismiss = { showThemePicker = false },
        )
    }

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
                        Text("PREDICT", fontWeight = FontWeight.Black, color = AccentPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onStandings) {
                        Icon(Icons.Filled.Leaderboard, contentDescription = stringResource(R.string.nav_standings), tint = AccentPrimary)
                    }
                    IconButton(onClick = onDashboard) {
                        Icon(Icons.Filled.Insights, contentDescription = stringResource(R.string.nav_dashboard), tint = AccentPrimary)
                    }
                    IconButton(onClick = { showThemePicker = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.nav_theme), tint = AccentPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    FilterBar(
                        state = state,
                        onLeague = viewModel::setLeague,
                        onSearch = viewModel::setSearchQuery,
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
                            item { Text(stringResource(R.string.empty_matches), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        // Open on the month the list is currently showing — the active range start (the current
        // round) when set, otherwise the first month fixtures exist in — and cap the year selector to
        // the loaded seasons. Anchoring on today would land on an empty month between seasons.
        val earliest = state.earliestDate ?: LocalDate.now()
        val latest = state.latestDate ?: earliest
        val anchor = state.fromDate ?: earliest
        val pickerState = rememberDateRangePickerState(
            initialDisplayedMonthMillis = anchor.withDayOfMonth(1).toEpochDay() * MILLIS_PER_DAY,
            yearRange = earliest.year..latest.year,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val from = pickerState.selectedStartDateMillis?.let { millisToDate(it) }
                    val to = pickerState.selectedEndDateMillis?.let { millisToDate(it) }
                    if (from != null) viewModel.setDateRange(from, to ?: from)
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, title = { Text(stringResource(R.string.date_range_picker_title), Modifier.padding(16.dp)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    state: MatchListUiState,
    onLeague: (LeagueType?) -> Unit,
    onSearch: (String) -> Unit,
    onGroup: (GroupMode) -> Unit,
    onOpenDatePicker: () -> Unit,
    onClearDate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Team search.
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search)) }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                focusedLeadingIconColor = AccentPrimary,
                cursorColor = AccentPrimary,
            ),
        )
        // League filter row (scrollable chips).
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LeagueChipFilter(stringResource(R.string.filter_all), state.leagueFilter == null) { onLeague(null) }
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
                val label = stringResource(if (mode == GroupMode.ROUND) R.string.group_by_round else R.string.group_by_week)
                LeagueChipFilter(label, state.groupMode == mode) { onGroup(mode) }
            }
            Box(Modifier.weight(1f))
            val hasRange = state.fromDate != null
            FilterChip(
                selected = hasRange,
                onClick = { if (hasRange) onClearDate() else onOpenDatePicker() },
                label = {
                    Text(
                        if (hasRange) "${state.fromDate!!.format(rangeFormatter)}–${state.toDate!!.format(rangeFormatter)}"
                        else stringResource(R.string.date_range),
                    )
                },
                leadingIcon = {
                    Icon(if (hasRange) Icons.Filled.Close else Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPrimary.copy(alpha = 0.2f)),
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
            selectedContainerColor = AccentPrimary.copy(alpha = 0.22f),
            selectedLabelColor = AccentPrimary,
        ),
    )
}

@Composable
private fun SectionHeader(title: SectionTitle, count: Int) {
    val text = when (title) {
        is SectionTitle.Round -> stringResource(R.string.section_round, title.number)
        is SectionTitle.Week -> stringResource(
            R.string.section_week,
            title.start.format(rangeFormatter),
            title.end.format(rangeFormatter),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = AccentPrimary)
    }
}

@Composable
private fun CalibrationStatusBar(state: MatchListUiState) {
    val status = state.calibration ?: return
    val applied = status.confidenceApplied || status.leaguesCalibrated.isNotEmpty()
    val color = if (applied) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val parts = buildList {
        add(stringResource(R.string.calib_prefix))
        add(stringResource(R.string.calib_results, status.recordedResults))
        status.overallHitRate?.let { add(stringResource(R.string.calib_accuracy, (it * 100).roundToInt())) }
        if (applied) {
            add(stringResource(if (status.confidenceApplied) R.string.calib_confidence_applied else R.string.calib_confidence_pending))
            add(stringResource(R.string.calib_leagues, status.leaguesCalibrated.size))
        } else {
            add(stringResource(R.string.calib_accumulating))
        }
    }
    Text(
        text = parts.joinToString(" · "),
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

        // Highlight the predicted winner: its name in the win colour and bold, the other side dimmed.
        val outcome = prediction?.predictedOutcome
        val winColor = WinColor
        val homeColor = when (outcome) {
            PredictedOutcome.HOME_WIN -> winColor
            PredictedOutcome.AWAY_WIN -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
        val awayColor = when (outcome) {
            PredictedOutcome.AWAY_WIN -> winColor
            PredictedOutcome.HOME_WIN -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(match.homeTeam.shortName, match.homeTeam.crestPrimary, match.homeTeam.crestSecondary)
            Text(
                match.homeTeam.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = homeColor,
                fontWeight = if (outcome == PredictedOutcome.HOME_WIN) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                match.awayTeam.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = awayColor,
                fontWeight = if (outcome == PredictedOutcome.AWAY_WIN) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            TeamCrest(match.awayTeam.shortName, match.awayTeam.crestPrimary, match.awayTeam.crestSecondary)
        }

        if (prediction != null) {
            val predColor = if (prediction.predictedOutcome == PredictedOutcome.DRAW) DrawColor else winColor
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prediction_label, outcomeLabel(match, prediction.predictedOutcome)),
                    style = MaterialTheme.typography.labelLarge,
                    color = predColor,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prediction.matchupEdge != MatchupEdge.NONE) MatchupChip(prediction.matchupEdge == MatchupEdge.HOME)
                    ConfidencePill(prediction.confidenceScore, prediction.confidenceTier)
                }
            }
        }
    }
}

@Composable
private fun outcomeLabel(match: Match, outcome: PredictedOutcome): String = when (outcome) {
    PredictedOutcome.HOME_WIN -> stringResource(R.string.outcome_win, match.homeTeam.displayName)
    PredictedOutcome.AWAY_WIN -> stringResource(R.string.outcome_win, match.awayTeam.displayName)
    PredictedOutcome.DRAW -> stringResource(R.string.outcome_draw)
}

@Composable
private fun LeagueChip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(AccentPrimary.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = AccentPrimary)
    }
}

@Composable
private fun MatchupChip(isHome: Boolean) {
    val color = if (isHome) AccentPrimary else LossColor
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(stringResource(R.string.matchup_edge), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfidencePill(score: Int, tier: ConfidenceTier) {
    val color = when (tier) {
        ConfidenceTier.VERY_HIGH, ConfidenceTier.HIGH -> AccentPrimary
        ConfidenceTier.MODERATE -> DrawColor
        ConfidenceTier.LOW, ConfidenceTier.VERY_LOW -> LossColor
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.hit_rate, score), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun millisToDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private const val MILLIS_PER_DAY = 86_400_000L
