package com.kickpredict.presentation.team

import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.usecase.TeamDetail
import com.kickpredict.presentation.components.TeamCrest
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.WinColor
import java.time.format.DateTimeFormatter

private val fixtureFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    teamId: String,
    onBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    viewModel: TeamViewModel = viewModel(factory = TeamViewModel.provideFactory(teamId)),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.team?.name ?: "Team", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val team = state.team
            when {
                state.isLoading -> CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.align(Alignment.Center))
                team == null -> Text(state.error ?: "Team not found", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                else -> TeamContent(team, onMatchClick)
            }
        }
    }
}

@Composable
private fun TeamContent(team: TeamDetail, onMatchClick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header(team) }
        if (team.recentResults.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.team_recent_form)) }
            item { FormRow(team) }
        }
        if (team.fixtures.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.team_fixtures)) }
        }
        items(team.fixtures, key = { it.id }) { m -> FixtureRow(m, team.teamId) { onMatchClick(m.id) } }
    }
}

@Composable
private fun Header(team: TeamDetail) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeamCrest(team.shortName, team.crestPrimary, team.crestSecondary, size = 48.dp)
            Column {
                Text(team.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(team.league.displayName, style = MaterialTheme.typography.labelSmall, color = AccentPrimary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.team_rank), team.standingRank?.let { "#$it" } ?: "-")
            Stat(stringResource(R.string.team_points), team.standing?.points?.toString() ?: "-")
            Stat("Elo", "${team.eloRating}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = AccentPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FormRow(team: TeamDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        team.recentResults.take(6).forEach { r ->
            val o = teamOutcome(r, team.teamId)
            val color = when (o) { 'W' -> WinColor; 'D' -> DrawColor; else -> LossColor }
            Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(o.toString(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FixtureRow(match: Match, teamId: String, onClick: () -> Unit) {
    val opponent = if (match.homeTeam.id == teamId) match.awayTeam else match.homeTeam
    val homeAway = stringResource(if (match.homeTeam.id == teamId) R.string.team_home else R.string.team_away)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(opponent.shortName, opponent.crestPrimary, opponent.crestSecondary, size = 28.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("$homeAway · ${opponent.displayName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text("R${match.round} · ${match.kickoff.format(fixtureFormatter)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        match.predictedResult?.let { Text(stringResource(R.string.hit_rate, it.confidenceScore), style = MaterialTheme.typography.labelSmall, color = AccentPrimary) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

private fun teamOutcome(r: RecordedResult, teamId: String): Char {
    val isHome = r.homeTeamId == teamId
    val gf = if (isHome) r.homeGoals else r.awayGoals
    val ga = if (isHome) r.awayGoals else r.homeGoals
    return when { gf > ga -> 'W'; gf < ga -> 'L'; else -> 'D' }
}
