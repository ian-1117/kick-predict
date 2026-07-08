package com.kickpredict.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.usecase.CalibrationDashboard
import com.kickpredict.domain.usecase.ReliabilityBucket
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.LimeGreen
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.WinColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("적중 · 보정 대시보드", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val dash = state.dashboard
            when {
                state.isLoading -> CircularProgressIndicator(color = LimeGreen, modifier = Modifier.align(Alignment.Center))
                dash == null || dash.totalResults == 0 -> EmptyState(Modifier.align(Alignment.Center))
                else -> DashboardContent(dash)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("아직 기록된 결과가 없습니다", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("경기 상세 화면에서 실제 결과를 입력하면\n적중률과 보정 현황이 여기에 쌓입니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (dash.reliability.isNotEmpty()) {
            item { SectionTitle("신뢰도 구간별 실제 적중률") }
            item { ReliabilityCard(dash.reliability) }
        }
        item { SectionTitle("최근 기록") }
        items(dash.recent, key = { it.matchId }) { ResultRow(it) }
    }
}

@Composable
private fun SummaryCard(dash: CalibrationDashboard) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("기록", "${dash.totalResults}건", MaterialTheme.colorScheme.onSurface)
            Stat("전체 적중률", "${(dash.overallHitRate * 100).roundToInt()}%", LimeGreen)
            val applied = dash.status.confidenceApplied || dash.status.leaguesCalibrated.isNotEmpty()
            Stat("AI 보정", if (applied) "적용" else "대기", if (applied) LimeGreen else DrawColor)
        }
        Text(
            text = "신뢰도 보정 ${if (dash.status.confidenceApplied) "적용" else "대기"} · 리그 ${dash.status.leaguesCalibrated.size}개 보정",
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
private fun ReliabilityCard(buckets: List<ReliabilityBucket>) {
    Card {
        Text(
            "막대: 실제 적중률 / 회색선: 예측 신뢰도 — 적중률이 신뢰도보다 낮으면 과신뢰",
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
                        Text("${(hit * 100).roundToInt()}% · ${b.count}건", style = MaterialTheme.typography.labelSmall, color = if (overconfident) LossColor else LimeGreen)
                    }
                    // bar fill = actual hit rate; colour flags over-confidence vs predicted band
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2A3320))) {
                        Box(Modifier.fillMaxWidth(hit.toFloat().coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(50)).background(if (overconfident) LossColor else LimeGreen))
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
                Text("예측 ${outcomeKo(r.predictedOutcome)} · 적중률 ${r.confidence}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            val color = if (r.wasCorrect) WinColor else LossColor
            Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (r.wasCorrect) "적중" else "실패", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
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

private fun outcomeKo(o: PredictedOutcome): String = when (o) {
    PredictedOutcome.HOME_WIN -> "홈 승"
    PredictedOutcome.AWAY_WIN -> "원정 승"
    PredictedOutcome.DRAW -> "무승부"
}
