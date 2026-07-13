package com.kickpredict.presentation.dashboard

import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.presentation.common.outcomeName

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.usecase.CalibrationDashboard
import com.kickpredict.domain.usecase.ModelKind
import com.kickpredict.domain.usecase.ModelScore
import com.kickpredict.domain.usecase.ReliabilityBucket
import com.kickpredict.domain.usecase.RoundAccuracy
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onBacktest: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = onBacktest) {
                        Icon(Icons.Filled.Science, contentDescription = stringResource(R.string.nav_backtest), tint = AccentPrimary)
                    }
                    if (state.isSeeding) {
                        CircularProgressIndicator(color = AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        IconButton(onClick = viewModel::seedSampleResults) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(R.string.dash_fill_samples), tint = AccentPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val dash = state.dashboard
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                dash == null || dash.totalResults == 0 -> EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    isSeeding = state.isSeeding,
                    onSeed = viewModel::seedSampleResults,
                )
                else -> DashboardContent(dash)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, isSeeding: Boolean, onSeed: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.dash_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.dash_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = onSeed,
            enabled = !isSeeding,
            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = MaterialTheme.colorScheme.background),
        ) {
            if (isSeeding) CircularProgressIndicator(color = MaterialTheme.colorScheme.background, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Text(stringResource(R.string.dash_fill_5rounds), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardContent(dash: CalibrationDashboard) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SummaryCard(dash) }
        if (dash.accuracyByRound.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_accuracy_by_round)) }
            item { AccuracyTrendCard(dash.accuracyByRound) }
        }
        if (dash.modelComparison.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_model_compare)) }
            item { ModelComparisonCard(dash.modelComparison) }
        }
        dash.scorecard?.let { scorecard ->
            item { SectionTitle(stringResource(R.string.dash_scorecard)) }
            item { ScorecardCard(scorecard) }
        }
        if (dash.reliability.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_reliability)) }
            item { ReliabilityCard(dash.reliability) }
        }
        item { SectionTitle(stringResource(R.string.dash_recent)) }
        items(dash.recent, key = { it.matchId }) { ResultRow(it) }
    }
}

@Composable
private fun SummaryCard(dash: CalibrationDashboard) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.dash_stat_records), stringResource(R.string.dash_records_value, dash.totalResults), MaterialTheme.colorScheme.onSurface)
            Stat(stringResource(R.string.dash_stat_accuracy), "${(dash.overallHitRate * 100).roundToInt()}%", AccentPrimary)
            val applied = dash.status.confidenceApplied || dash.status.leaguesCalibrated.isNotEmpty()
            Stat(stringResource(R.string.calib_prefix), stringResource(if (applied) R.string.dash_applied else R.string.dash_pending), if (applied) AccentPrimary else DrawColor)
        }
        val confState = stringResource(if (dash.status.confidenceApplied) R.string.dash_applied else R.string.dash_pending)
        Text(
            text = stringResource(R.string.dash_confidence_line, confState, dash.status.leaguesCalibrated.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScorecardCard(sc: com.kickpredict.domain.model.ModelScorecard) {
    Card {
        Text(
            stringResource(R.string.dash_scorecard_note, sc.sampleCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.dash_brier), String.format("%.3f", sc.brierScore), AccentPrimary)
            Stat(stringResource(R.string.dash_logloss), String.format("%.3f", sc.logLoss), AccentPrimary)
            Stat(stringResource(R.string.dash_calib_err), String.format("%.1f%%", sc.calibrationError * 100), AccentPrimary)
        }
    }
}

@Composable
private fun ModelComparisonCard(models: List<ModelScore>) {
    val ranked = models.sortedByDescending { it.accuracy }
    val maxAcc = ranked.firstOrNull()?.accuracy?.coerceAtLeast(0.01) ?: 1.0
    Card {
        Text(
            stringResource(R.string.dash_model_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ranked.forEachIndexed { index, m ->
                val best = index == 0
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val modelName = stringResource(
                            when (m.kind) {
                                ModelKind.ENGINE -> R.string.model_engine
                                ModelKind.ELO -> R.string.model_elo
                                ModelKind.BASELINE -> R.string.model_baseline
                            },
                        )
                        Text(modelName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("${(m.accuracy * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = if (best) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f))) {
                        Box(Modifier.fillMaxWidth((m.accuracy / maxAcc).toFloat().coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(50)).background(if (best) AccentPrimary else AccentPrimary.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyTrendCard(rounds: List<RoundAccuracy>) {
    Card {
        Text(
            stringResource(R.string.dash_round_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(130.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rounds.forEach { r ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${(r.accuracy * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = AccentPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth(0.55f)
                            .height((r.accuracy * 100).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(AccentPrimary),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rounds.forEach { r ->
                Text("R${r.round}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReliabilityCard(buckets: List<ReliabilityBucket>) {
    Card {
        Text(
            stringResource(R.string.dash_reliability_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            buckets.forEach { b ->
                val hit = b.hitRate
                val conf = b.avgConfidence / 100.0
                val overconfident = hit < conf - 0.05
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.rangeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.dash_band_value, (hit * 100).roundToInt(), b.count), style = MaterialTheme.typography.labelSmall, color = if (overconfident) LossColor else AccentPrimary)
                    }
                    // bar fill = actual hit rate; colour flags over-confidence vs predicted band
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f))) {
                        Box(Modifier.fillMaxWidth(hit.toFloat().coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(50)).background(if (overconfident) LossColor else AccentPrimary))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: RecordedResult) {
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${r.homeTeam}  ${r.scoreline}  ${r.awayTeam}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.dash_recent_pred, outcomeName(r.predictedOutcome), r.confidence), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            val color = if (r.wasCorrect) WinColor else LossColor
            Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(stringResource(if (r.wasCorrect) R.string.result_hit else R.string.result_miss), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        content = content,
    )
}
