package com.kickpredict.presentation.backtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.R
import com.kickpredict.domain.model.BacktestLeagueResult
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.WinColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    onBack: () -> Unit,
    viewModel: BacktestViewModel = viewModel(factory = BacktestViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backtest_title), fontWeight = FontWeight.Bold) },
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
            val report = state.report
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                report == null || report.overall.games == 0 -> Text(
                    stringResource(R.string.backtest_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { OverallCard(report.overall) }
                    item {
                        Text(
                            stringResource(R.string.backtest_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.backtest_by_league),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(report.byLeague, key = { it.league?.name ?: "all" }) { LeagueRow(it) }
                }
            }
        }
    }
}

@Composable
private fun OverallCard(r: BacktestLeagueResult) {
    Card {
        Text(
            stringResource(R.string.backtest_subtitle, r.games),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.dash_stat_accuracy), "${(r.accuracy * 100).roundToInt()}%", AccentPrimary)
            Stat(stringResource(R.string.dash_brier), String.format("%.3f", r.brier), MaterialTheme.colorScheme.onSurface)
            Stat(stringResource(R.string.dash_logloss), String.format("%.3f", r.logLoss), MaterialTheme.colorScheme.onSurface)
        }
        if (r.bets > 0) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val roiColor = if (r.roiPercent >= 0) WinColor else LossColor
                Stat(stringResource(R.string.backtest_roi), String.format("%+d%%", r.roiPercent), roiColor)
                Stat(stringResource(R.string.backtest_bets, r.bets), "${(r.betsWon * 100.0 / r.bets).roundToInt()}%", MaterialTheme.colorScheme.onSurface)
                val clvColor = if (r.clvTenths >= 0) WinColor else LossColor
                Stat(stringResource(R.string.backtest_clv), String.format("%+.1f%%", r.clvTenths / 10.0), clvColor)
            }
        }
    }
}

@Composable
private fun LeagueRow(r: BacktestLeagueResult) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            r.league?.displayName ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.backtest_league_stats, r.games, (r.accuracy * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.bets > 0) {
            val color = if (r.roiPercent >= 0) WinColor else LossColor
            Text(
                stringResource(R.string.backtest_league_value, r.roiPercent, r.bets, String.format("%+.1f", r.clvTenths / 10.0)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
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
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        content = content,
    )
}
