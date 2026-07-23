package com.kickpredict.presentation.dashboard

import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.presentation.common.outcomeName

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.DriftWindow
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.usecase.CalibrationDashboard
import com.kickpredict.domain.usecase.LeagueScore
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
        if (dash.drift.size >= 2) {
            item { SectionTitle(stringResource(R.string.dash_drift)) }
            item { DriftCard(dash.drift) }
        }
        if (dash.byLeague.size > 1) {
            item { SectionTitle(stringResource(R.string.dash_by_league)) }
            item { LeagueBreakdownCard(dash.byLeague) }
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

/**
 * Brier over chronological windows — is the model getting sharper or drifting? The line is inverted
 * so *up = sharper* (lower Brier sits higher); a verdict compares the newest window to the oldest.
 */
@Composable
private fun DriftCard(windows: List<DriftWindow>) {
    val oldest = windows.first().brierScore
    val newest = windows.last().brierScore
    val sharper = newest < oldest
    val verdictColor = if (sharper) WinColor else LossColor
    Card {
        Text(
            stringResource(R.string.dash_drift_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (sharper) R.string.dash_drift_sharper else R.string.dash_drift_drifting),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = verdictColor,
            )
            Text(
                stringResource(R.string.dash_drift_delta, String.format("%.3f", oldest), String.format("%.3f", newest)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        DriftChart(windows.map { it.brierScore }, verdictColor)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dash_drift_oldest), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.dash_drift_newest), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Line of Brier across windows, drawn inverted so a lower (sharper) Brier sits higher on the chart. */
@Composable
private fun DriftChart(brier: List<Double>, lineColor: Color) {
    val maxV = brier.max()
    val minV = brier.min()
    val span = (maxV - minV).coerceAtLeast(1e-6)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width
        val h = size.height
        fun px(i: Int) = if (brier.size == 1) 0f else w * i / (brier.size - 1)
        // Invert: highest Brier (worst) at the bottom, lowest (sharpest) at the top.
        fun py(v: Double) = (h * ((v - minV) / span)).toFloat()

        val line = Path().apply {
            moveTo(px(0), py(brier[0]))
            brier.forEachIndexed { i, v -> lineTo(px(i), py(v)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(px(brier.lastIndex), h)
            lineTo(px(0), h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.26f), lineColor.copy(alpha = 0.02f))))
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        brier.indices.forEach { i ->
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(px(i), py(brier[i])))
        }
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
private fun LeagueBreakdownCard(leagues: List<LeagueScore>) {
    // Lowest Brier = sharpest league; flag it so "where do I trust the model" reads at a glance.
    val sharpest = leagues.minByOrNull { it.brierScore }?.league
    Card {
        Text(
            stringResource(R.string.dash_by_league_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            leagues.forEach { l ->
                val best = l.league == sharpest
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(l.league.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("${(l.hitRate * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = if (best) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.dash_league_meta, l.count, String.format("%.3f", l.brierScore)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(OutlineColor.copy(alpha = 0.45f))) {
                        Box(Modifier.fillMaxWidth(l.hitRate.toFloat().coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(50)).background(if (best) AccentPrimary else AccentPrimary.copy(alpha = 0.4f)))
                    }
                }
            }
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
