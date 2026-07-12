package com.kickpredict.presentation.detail

import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.presentation.common.outcomeName

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.presentation.components.ConfidenceBadge
import com.kickpredict.presentation.components.PowerComparisonReport
import com.kickpredict.presentation.components.PredictionDonutChart
import com.kickpredict.presentation.components.ProbabilityGauges
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor
import java.time.format.DateTimeFormatter

private val detailFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm")

/**
 * Width at which we switch from the stacked (cover-screen) layout to the two-pane (unfolded)
 * layout. ~600dp comfortably separates the Fold 3 cover screen from the main screen.
 */
private const val EXPANDED_WIDTH_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionDetailScreen(
    matchId: String,
    onBack: () -> Unit,
    viewModel: PredictionDetailViewModel =
        viewModel(factory = PredictionDetailViewModel.provideFactory(matchId)),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.match?.let { "${it.homeTeam.shortName} vs ${it.awayTeam.shortName}" }
                            ?: "Prediction",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val match = state.match
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
                match?.predictedResult == null -> Text(
                    text = state.error ?: "No prediction available",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> ResponsivePredictionContent(
                    match = match,
                    prediction = match.predictedResult!!,
                    actualResult = state.actualResult,
                    recordedCount = state.recordedResultCount,
                    onSaveResult = viewModel::recordResult,
                )
            }
        }
    }
}

@Composable
private fun ResponsivePredictionContent(
    match: Match,
    prediction: PredictionResult,
    actualResult: com.kickpredict.domain.model.ActualResult?,
    recordedCount: Int,
    onSaveResult: (Int, Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= EXPANDED_WIDTH_DP.dp
        if (isExpanded) {
            // Unfolded / main screen: side-by-side two-pane layout.
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    MatchHeader(match)
                    Spacer(Modifier.height(16.dp))
                    PowerComparisonReport(
                        home = match.homeTeam,
                        away = match.awayTeam,
                        rationale = prediction.rationale,
                        headToHead = match.headToHead,
                        matchupEdge = prediction.matchupEdge,
                        matchupStrengthPercent = prediction.matchupStrengthPercent,
                        context = match.context,
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ConfidenceBadge(prediction.confidenceScore, prediction.confidenceTier)
                    ExpectedScoreLabel(prediction.expectedScoreline)
                    Spacer(Modifier.height(20.dp))
                    PredictionDonutChart(prediction, modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(Modifier.height(24.dp))
                    ProbabilityGauges(prediction, match.homeTeam.shortName, match.awayTeam.shortName)
                    Spacer(Modifier.height(16.dp))
                    MarketSummary(prediction)
                    Spacer(Modifier.height(16.dp))
                    ScorelineDistribution(prediction)
                    Spacer(Modifier.height(20.dp))
                    ResultEntrySection(match, prediction, actualResult, recordedCount, onSaveResult)
                }
            }
        } else {
            // Cover screen / phone: single scrolling column.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MatchHeader(match)
                ConfidenceBadge(prediction.confidenceScore, prediction.confidenceTier)
                PredictionDonutChart(prediction, modifier = Modifier.fillMaxWidth(0.7f))
                ProbabilityGauges(prediction, match.homeTeam.shortName, match.awayTeam.shortName)
                MarketSummary(prediction)
                ScorelineDistribution(prediction)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                ) {
                    PowerComparisonReport(
                        home = match.homeTeam,
                        away = match.awayTeam,
                        rationale = prediction.rationale,
                        headToHead = match.headToHead,
                        matchupEdge = prediction.matchupEdge,
                        matchupStrengthPercent = prediction.matchupStrengthPercent,
                        context = match.context,
                    )
                }
                ResultEntrySection(match, prediction, actualResult, recordedCount, onSaveResult)
            }
        }
    }
}

@Composable
private fun ExpectedScoreLabel(scoreline: String) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.detail_expected_score),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = scoreline,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ResultEntrySection(
    match: Match,
    prediction: PredictionResult,
    actualResult: ActualResult?,
    recordedCount: Int,
    onSave: (Int, Int) -> Unit,
) {
    var home by remember(actualResult) { mutableIntStateOf(actualResult?.homeGoals ?: 0) }
    var away by remember(actualResult) { mutableIntStateOf(actualResult?.awayGoals ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_enter_result), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.detail_recorded_count, recordedCount), style = MaterialTheme.typography.labelSmall, color = AccentPrimary)
        }

        if (actualResult != null) {
            val correct = prediction.predictedOutcome == actualResult.outcome
            val color = if (correct) WinColor else LossColor
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))
                        .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(if (correct) R.string.detail_prediction_hit else R.string.detail_prediction_miss), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    stringResource(R.string.prediction_label, outcomeName(prediction.predictedOutcome)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            GoalStepper(match.homeTeam.shortName, home) { home = it }
            Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GoalStepper(match.awayTeam.shortName, away) { away = it }
        }

        Button(
            onClick = { onSave(home, away) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = MaterialTheme.colorScheme.background),
        ) {
            Text(stringResource(if (actualResult == null) R.string.detail_save_result else R.string.detail_edit_result), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GoalStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.detail_decrease), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "$value",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.size(width = 44.dp, height = 40.dp).padding(top = 4.dp),
            )
            IconButton(onClick = { onChange(value + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.detail_increase), tint = AccentPrimary)
            }
        }
    }
}

@Composable
private fun MarketSummary(prediction: PredictionResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.detail_extra_markets), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        MarketRow(stringResource(R.string.detail_over25), prediction.overProbabilityPercent, stringResource(R.string.detail_under25), prediction.underProbabilityPercent)
        MarketRow(stringResource(R.string.detail_btts), prediction.bttsProbabilityPercent, stringResource(R.string.detail_no_goal), prediction.noBttsProbabilityPercent)
    }
}

@Composable
private fun MarketRow(yesLabel: String, yesPercent: Int, noLabel: String, noPercent: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$yesLabel $yesPercent%", style = MaterialTheme.typography.labelLarge, color = AccentPrimary, fontWeight = FontWeight.Bold)
            Text("$noLabel $noPercent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f))) {
            Box(Modifier.fillMaxWidth((yesPercent / 100f).coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(AccentPrimary))
        }
    }
}

@Composable
private fun ScorelineDistribution(prediction: PredictionResult) {
    val lines = prediction.topScorelines
    if (lines.isEmpty()) return
    val maxPercent = lines.maxOf { it.probabilityPercent }.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.detail_score_prob), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        lines.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(56.dp),
                )
                Box(
                    Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f)),
                ) {
                    Box(Modifier.fillMaxWidth((s.probabilityPercent.toFloat() / maxPercent).coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(AccentPrimary))
                }
                Text(
                    "${s.probabilityPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MatchHeader(match: Match) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${match.league.displayName} · ${match.venue}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${match.homeTeam.displayName}  vs  ${match.awayTeam.displayName}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = match.kickoff.format(detailFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
