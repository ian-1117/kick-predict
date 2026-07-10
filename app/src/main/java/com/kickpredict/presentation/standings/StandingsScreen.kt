package com.kickpredict.presentation.standings

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.Standing
import com.kickpredict.presentation.theme.LimeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onSimulate: () -> Unit,
    viewModel: StandingsViewModel = viewModel(factory = StandingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("리그 순위", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onSimulate) {
                        Icon(Icons.Filled.Timeline, contentDescription = "시즌 시뮬레이션", tint = LimeGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(color = LimeGreen, modifier = Modifier.align(Alignment.Center))
                state.leaguesWithData.isEmpty() -> Text(
                    "아직 결과가 없습니다. 결과를 입력하거나 대시보드에서 샘플을 채우면\n순위표가 만들어집니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.leaguesWithData.forEach { league ->
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
                    HeaderRow()
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(state.table, key = { _, s -> s.teamId }) { index, s ->
                            StandingRow(rank = index + 1, s = s, onClick = { onTeamClick(s.teamId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(width = 28.dp, height = 16.dp))
        Text("팀", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
        StatCell("경기"); StatCell("승점")
    }
}

@Composable
private fun StandingRow(rank: Int, s: Standing, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = when {
            rank <= 3 -> LimeGreen
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(badge.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", style = MaterialTheme.typography.labelSmall, color = badge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(s.teamName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(
                "${s.won}승 ${s.drawn}무 ${s.lost}패 · 득실 ${if (s.goalDiff >= 0) "+" else ""}${s.goalDiff}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${s.played}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.size(width = 44.dp, height = 20.dp))
        Text("${s.points}", style = MaterialTheme.typography.titleMedium, color = LimeGreen, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.size(width = 44.dp, height = 24.dp))
    }
}

@Composable
private fun StatCell(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.size(width = 44.dp, height = 16.dp))
}
