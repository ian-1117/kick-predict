package com.kickpredict.presentation.components

import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.presentation.common.weatherName
import com.kickpredict.presentation.common.rationaleText
import com.kickpredict.domain.model.RationaleNote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.MatchupEdge
import com.kickpredict.domain.model.TeamProfile
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import com.kickpredict.presentation.theme.DrawColor
import com.kickpredict.presentation.theme.AccentPrimary
import com.kickpredict.presentation.theme.LossColor
import com.kickpredict.presentation.theme.WinColor
import kotlin.math.roundToInt

/**
 * Left-hand "power comparison report": mirrored metric bars for the two teams plus the engine's
 * rationale notes. Uses weight-based layout so the two columns stay balanced at any width.
 */
@Composable
fun PowerComparisonReport(
    home: TeamProfile,
    away: TeamProfile,
    rationale: List<RationaleNote>,
    modifier: Modifier = Modifier,
    headToHead: HeadToHead? = null,
    matchupEdge: MatchupEdge = MatchupEdge.NONE,
    matchupStrengthPercent: Int = 0,
    context: MatchContext? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.power_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TeamHeader(home, away)

        if (matchupEdge != MatchupEdge.NONE) {
            MatchupBanner(
                favored = if (matchupEdge == MatchupEdge.HOME) home else away,
                isHome = matchupEdge == MatchupEdge.HOME,
                strengthPercent = matchupStrengthPercent,
                headToHead = headToHead,
            )
        }

        MetricRow("Rating", home.overallRating / 100.0, away.overallRating / 100.0,
            home.overallRating.roundToInt().toString(), away.overallRating.roundToInt().toString())
        MetricRow("Position", positionFraction(home.leaguePosition), positionFraction(away.leaguePosition),
            positionLabel(home.leaguePosition), positionLabel(away.leaguePosition))
        MetricRow("Form", home.formScore, away.formScore,
            pct(home.formScore), pct(away.formScore))
        MetricRow("Attack", home.goalsScoredAvg / 3.0, away.goalsScoredAvg / 3.0,
            "${home.goalsScoredAvg}", "${away.goalsScoredAvg}")
        MetricRow("Defense", 1 - home.goalsConcededAvg / 3.0, 1 - away.goalsConcededAvg / 3.0,
            "${home.goalsConcededAvg}", "${away.goalsConcededAvg}")

        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FormChips(home.recentForm)
            FormChips(away.recentForm)
        }

        if (context != null && context.hasVariable) {
            MatchVariablesRow(home.shortName, away.shortName, context)
        }

        if (rationale.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.power_why),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            rationale.forEach { note ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("· ", color = AccentPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = rationaleText(note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchupBanner(
    favored: TeamProfile,
    isHome: Boolean,
    strengthPercent: Int,
    headToHead: HeadToHead?,
) {
    val accent = if (isHome) WinColor else LossColor
    val label = stringResource(
        when {
            strengthPercent >= 60 -> R.string.power_bogey
            strengthPercent >= 30 -> R.string.power_edge
            else -> R.string.power_slight_edge
        },
    )
    val record = headToHead?.let {
        val (w, d, l) = if (isHome) Triple(it.homeWins, it.draws, it.awayWins)
        else Triple(it.awayWins, it.draws, it.homeWins)
        stringResource(R.string.power_record, w, d, l)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.power_matchup, label, favored.name),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            if (record != null) {
                Text(
                    text = record,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (headToHead != null && headToHead.recentMeetings.isNotEmpty()) {
                RecentMeetingChips(headToHead.recentMeetings)
            }
        }
    }
}

/**
 * Most-recent-first H2H chips (left = latest meeting), from the home team's perspective.
 * A ring marks meetings played at this fixture's venue (홈/원정 구분).
 */
@Composable
private fun RecentMeetingChips(meetings: List<H2HMeeting>) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.power_recent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        meetings.take(5).forEach { meeting ->
            val color = when (meeting.outcome) {
                H2HOutcome.HOME_WIN -> WinColor
                H2HOutcome.DRAW -> DrawColor
                H2HOutcome.AWAY_WIN -> LossColor
            }
            val venueMod = if (meeting.atHomeVenue) {
                Modifier.border(1.5.dp, color, CircleShape)
            } else Modifier
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .then(venueMod),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = meeting.outcome.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TeamHeader(home: TeamProfile, away: TeamProfile) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(home.shortName, home.crestPrimary, home.crestSecondary, size = 32.dp)
        Text(
            text = home.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = WinColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = "VS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            text = away.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = LossColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        TeamCrest(away.shortName, away.crestPrimary, away.crestSecondary, size = 32.dp)
    }
}

@Composable
private fun MatchVariablesRow(homeShort: String, awayShort: String, context: MatchContext) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.power_factors),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (context.weather.isAdverse) {
                VariableChip(stringResource(R.string.power_weather, weatherName(context.weather)), DrawColor)
            }
            if (context.homeAvailability.isWeakened) {
                VariableChip(
                    stringResource(R.string.power_availability, homeShort, context.homeAvailability.keyPlayersInjured, context.homeAvailability.lineupStrengthPercent),
                    LossColor,
                )
            }
            if (context.awayAvailability.isWeakened) {
                VariableChip(
                    stringResource(R.string.power_availability, awayShort, context.awayAvailability.keyPlayersInjured, context.awayAvailability.lineupStrengthPercent),
                    LossColor,
                )
            }
        }
    }
}

@Composable
private fun VariableChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MetricRow(
    label: String,
    homeValue: Double,
    awayValue: Double,
    homeText: String,
    awayText: String,
) {
    val h = homeValue.coerceIn(0.0, 1.0).toFloat()
    val a = awayValue.coerceIn(0.0, 1.0).toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(homeText, style = MaterialTheme.typography.labelSmall, color = WinColor)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(awayText, style = MaterialTheme.typography.labelSmall, color = LossColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home bar grows leftwards from the centre.
            Box(
                modifier = Modifier.weight(1f).height(8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(h)
                        .height(8.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(WinColor),
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier.weight(1f).height(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(a)
                        .height(8.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(LossColor),
                )
            }
        }
    }
}

@Composable
private fun FormChips(form: List<MatchOutcome>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (form.isEmpty()) {
            Text(
                "No form data",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        form.take(5).forEach { outcome ->
            val color = when (outcome) {
                MatchOutcome.WIN -> WinColor
                MatchOutcome.DRAW -> DrawColor
                MatchOutcome.LOSS -> LossColor
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = outcome.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

// Live teams with no standings row yet (e.g. a just-promoted side) carry position 0 — show it as
// unknown ("-", a half-filled bar) rather than a misleading "#0" at the top of the table.
private fun positionLabel(position: Int): String = if (position <= 0) "-" else "#$position"
private fun positionFraction(position: Int): Double =
    if (position <= 0) 0.5 else ((21 - position) / 20.0).coerceIn(0.0, 1.0)
