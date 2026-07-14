package com.kickpredict.presentation.valuepicks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.R
import com.kickpredict.domain.model.AccumulatorSummary
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.ValuePickStatus
import com.kickpredict.domain.model.ValuePicksReport
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.OutlineColor
import com.kickpredict.presentation.theme.WinColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val pickDateFormatter = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValuePicksScreen(
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    viewModel: ValuePicksViewModel = viewModel(factory = ValuePicksViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_value_picks), fontWeight = FontWeight.Bold) },
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
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                state.report.totalCount == 0 -> Text(
                    stringResource(R.string.value_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    RoiHeader(state.report)
                    if (state.leagues.size > 1) {
                        LeagueFilterRow(
                            leagues = state.leagues,
                            selected = state.leagueFilter,
                            onSelect = viewModel::setLeague,
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                        // Leave room at the bottom so the accumulator bar never covers the last pick.
                        val bottomPad = if (state.accumulator != null) 200.dp else 16.dp
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.report.picks, key = { it.matchId }) { pick ->
                                ValuePickRow(
                                    pick = pick,
                                    selected = pick.matchId in state.selectedLegIds,
                                    onClick = { onMatchClick(pick.matchId) },
                                    onToggle = { viewModel.toggleLeg(pick) },
                                )
                            }
                        }
                        state.accumulator?.let { acca ->
                            AccumulatorBar(
                                acca = acca,
                                onClear = viewModel::clearAccumulator,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoiHeader(report: ValuePicksReport) {
    val roiColor = if (report.settledCount == 0) MaterialTheme.colorScheme.onSurface
    else if (report.roiPercent >= 0) WinColor else LossColor
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (report.settledCount == 0) "—" else String.format("%+d%%", report.roiPercent),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = roiColor,
        )
        Text(
            stringResource(R.string.value_roi_caption, report.settledCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
            CountStat(stringResource(R.string.value_won), report.wonCount, WinColor)
            CountStat(stringResource(R.string.value_lost), report.lostCount, LossColor)
            CountStat(stringResource(R.string.value_pending_count), report.pendingCount, AccentPrimary)
        }
        if (report.hasClv) {
            val clvColor = when {
                report.clvTenths > 0 -> WinColor
                report.clvTenths < 0 -> LossColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.value_clv_label, String.format("%+.1f", report.clvTenths / 10.0)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = clvColor,
                )
                Text(
                    stringResource(R.string.value_clv_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (report.settledCount > 0) {
            val bankrollColor = if (report.finalBankroll >= 1.0) WinColor else LossColor
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "×${String.format("%.2f", report.finalBankroll)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = bankrollColor,
                )
                Text(
                    stringResource(R.string.value_bankroll_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (report.roiTrend.size >= 2) {
            Spacer(Modifier.height(6.dp))
            ChartBlock(stringResource(R.string.value_roi_chart), report.roiTrend, roiColor)
            Spacer(Modifier.height(10.dp))
            val bankrollColor = if (report.finalBankroll >= 1.0) WinColor else LossColor
            ChartBlock(stringResource(R.string.value_bankroll_chart), report.bankrollTrend, bankrollColor)
        }
    }
}

/** A small captioned sparkline block used for the ROI and bankroll curves. */
@Composable
private fun ChartBlock(label: String, trend: List<Int>, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RoiTrendChart(trend, color)
}

/**
 * Cumulative-ROI sparkline: how the flat-stake ROI has moved as each settled pick landed, oldest to
 * newest. A dashed zero baseline separates profit from loss; the curve is win- or loss-coloured by
 * where it currently sits, with a soft area fill down to break-even.
 */
@Composable
private fun RoiTrendChart(trend: List<Int>, lineColor: Color) {
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxV = trend.max().coerceAtLeast(0)
    val minV = trend.min().coerceAtMost(0)
    val span = (maxV - minV).coerceAtLeast(1).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(top = 4.dp),
    ) {
        val w = size.width
        val h = size.height
        fun px(i: Int) = if (trend.size == 1) 0f else w * i / (trend.size - 1)
        fun py(v: Int) = h - (v - minV) / span * h
        val zeroY = py(0)

        // Dashed break-even line.
        drawLine(
            color = baselineColor.copy(alpha = 0.4f),
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )

        val line = Path().apply {
            moveTo(px(0), py(trend[0]))
            trend.forEachIndexed { i, v -> lineTo(px(i), py(v)) }
        }
        // Area fill from the curve down to break-even.
        val fill = Path().apply {
            addPath(line)
            lineTo(px(trend.lastIndex), zeroY)
            lineTo(px(0), zeroY)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))),
        )
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(px(trend.lastIndex), py(trend.last())))
    }
}

@Composable
private fun CountStat(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LeagueFilterRow(
    leagues: List<com.kickpredict.domain.model.LeagueType>,
    selected: com.kickpredict.domain.model.LeagueType?,
    onSelect: (com.kickpredict.domain.model.LeagueType?) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AccentPrimary.copy(alpha = 0.22f),
        selectedLabelColor = AccentPrimary,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.filter_all)) }, colors = colors)
        leagues.forEach { league ->
            FilterChip(selected = selected == league, onClick = { onSelect(league) }, label = { Text(league.displayName) }, colors = colors)
        }
    }
}

/**
 * Sticky "bet slip" for the accumulator being built: combined odds, joint probability, the parlay
 * edge, and the half-Kelly stake. Appears once two or more legs are selected.
 */
@Composable
private fun AccumulatorBar(acca: AccumulatorSummary, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val edgeColor = if (acca.edgePercent >= 0) WinColor else LossColor
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.acca_title_legs, acca.legCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.acca_clear)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AccaStat(stringResource(R.string.acca_combo_odds), "@${String.format("%.2f", acca.comboOdds)}", MaterialTheme.colorScheme.onSurface)
            AccaStat(stringResource(R.string.acca_joint_prob), "${acca.jointModelPercent}%", MaterialTheme.colorScheme.onSurface)
            AccaStat(stringResource(R.string.acca_edge), String.format("%+d%%", acca.edgePercent), edgeColor)
            AccaStat(stringResource(R.string.acca_kelly), "${acca.kellyStakePercent}%", AccentPrimary)
        }
    }
}

@Composable
private fun AccaStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ValuePickRow(
    pick: ValuePick,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val selectable = pick.status == ValuePickStatus.PENDING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (selected) Modifier.border(1.5.dp, AccentPrimary, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = stringResource(if (selected) R.string.acca_remove_leg else R.string.acca_add_leg),
                    tint = if (selected) AccentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pickLabel(pick),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${pick.league.displayName} · ${formatKickoff(pick.kickoffEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@${String.format("%.2f", pick.odds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+${pick.edge}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AccentPrimary)
            }
            StatusPill(pick)
        }
    }
}

@Composable
private fun StatusPill(pick: ValuePick) {
    val (label, color) = when (pick.status) {
        ValuePickStatus.WON -> stringResource(R.string.result_hit) to WinColor
        ValuePickStatus.LOST -> stringResource(R.string.result_miss) to LossColor
        ValuePickStatus.PENDING -> stringResource(R.string.value_pending_count) to AccentPrimary
    }
    val text = if (pick.status == ValuePickStatus.PENDING) label
    else "$label · ${String.format("%+.2f", pick.profit)}"
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun pickLabel(pick: ValuePick): String = when (pick.pickedOutcome) {
    PredictedOutcome.HOME_WIN -> stringResource(R.string.outcome_win, pick.homeTeam)
    PredictedOutcome.AWAY_WIN -> stringResource(R.string.outcome_win, pick.awayTeam)
    PredictedOutcome.DRAW -> "${pick.homeTeam} vs ${pick.awayTeam} · ${stringResource(R.string.outcome_draw)}"
}

private fun formatKickoff(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(pickDateFormatter)
