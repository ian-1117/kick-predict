package com.kickpredict.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kickpredict.KickPredictApplication
import com.kickpredict.R
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.usecase.GetValuePicksUseCase
import com.kickpredict.presentation.MainActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: a glanceable list of the next upcoming picks, value picks first, each with the
 * predicted result and (when it's a value pick) the model's edge over the market. Tapping opens the app.
 */
class KickPredictWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val rows = loadRows(context)
        provideContent { WidgetContent(rows) }
    }

    private suspend fun loadRows(context: Context): List<WidgetRow> {
        val container = (context.applicationContext as KickPredictApplication).container
        // Cached fixtures only — the widget must render fast and offline.
        val matches = runCatching { container.getPredictedMatches.cached() }.getOrNull().orEmpty()
        val byId = matches.associateBy { it.id }
        val odds = runCatching { container.odds() }.getOrDefault(emptyMap())
        val live = runCatching { container.liveScores() }.getOrDefault(emptyMap())
        val results = runCatching { container.calibrationRepository.recordedResults().associateBy { it.matchId } }
            .getOrDefault(emptyMap())
        val followed = runCatching { container.followPreference.followed.value }.getOrDefault(emptySet())
        val config = WidgetConfigPreference(context).filter
        fun include(m: Match): Boolean = when (config) {
            WidgetConfigPreference.FILTER_ALL -> true
            WidgetConfigPreference.FILTER_FOLLOWED -> m.homeTeam.id in followed || m.awayTeam.id in followed
            else -> m.league.name == config
        }
        val now = LocalDateTime.now()

        // In-play matches come first, with their current score.
        val liveRows = live.mapNotNull { (id, score) ->
            val match = byId[id]?.takeIf { include(it) } ?: return@mapNotNull null
            WidgetRow(
                matchup = "${match.homeTeam.shortName} vs ${match.awayTeam.shortName}",
                prediction = match.predictedResult?.let {
                    predictionLabel(context, it.predictedOutcome, match.homeTeam.displayName, match.awayTeam.displayName)
                }.orEmpty(),
                kickoff = "",
                edge = null,
                kickoffMillis = match.kickoff,
                liveScore = score.scoreline,
                liveMinute = score.minute,
            )
        }

        // Recently-finished predictions with their result — followed teams first, most recent first.
        val recentCutoff = now.minusDays(RESULT_WINDOW_DAYS)
        val resultRows = matches
            .filter { it.id !in live && it.kickoff.isAfter(recentCutoff) && results.containsKey(it.id) && include(it) }
            .sortedWith(
                compareByDescending<Match> { followed.isNotEmpty() && (it.homeTeam.id in followed || it.awayTeam.id in followed) }
                    .thenByDescending { it.kickoff },
            )
            .mapNotNull { match ->
                val result = results[match.id] ?: return@mapNotNull null
                WidgetRow(
                    matchup = "${match.homeTeam.shortName} vs ${match.awayTeam.shortName}",
                    prediction = "",
                    kickoff = "",
                    edge = null,
                    kickoffMillis = match.kickoff,
                    resultScore = "${result.homeGoals} – ${result.awayGoals}",
                    resultHit = result.wasCorrect,
                )
            }
            .take(MAX_RESULT_ROWS)

        // Then upcoming picks, value picks first.
        val upcomingRows = matches
            .filter { it.predictedResult != null && it.kickoff.isAfter(now) && it.id !in live && include(it) }
            .map { match ->
                val prediction = match.predictedResult!!
                val outcome = prediction.predictedOutcome
                val edge = odds[match.id]?.let {
                    modelPercent(prediction, outcome) - it.percentFor(outcome)
                }
                val isValue = edge != null && edge >= GetValuePicksUseCase.VALUE_EDGE_THRESHOLD
                WidgetRow(
                    matchup = "${match.homeTeam.shortName} vs ${match.awayTeam.shortName}",
                    prediction = predictionLabel(context, outcome, match.homeTeam.displayName, match.awayTeam.displayName),
                    kickoff = match.kickoff.format(KICKOFF_FORMAT),
                    edge = if (isValue) edge else null,
                    kickoffMillis = match.kickoff,
                )
            }
            .sortedWith(compareByDescending<WidgetRow> { it.edge != null }.thenBy { it.kickoffMillis })

        return (liveRows + resultRows + upcomingRows).take(MAX_ROWS)
    }

    private fun predictionLabel(context: Context, outcome: PredictedOutcome, home: String, away: String): String =
        when (outcome) {
            PredictedOutcome.HOME_WIN -> context.getString(R.string.outcome_win, home)
            PredictedOutcome.AWAY_WIN -> context.getString(R.string.outcome_win, away)
            PredictedOutcome.DRAW -> context.getString(R.string.outcome_draw)
        }

    private fun modelPercent(p: PredictionResult, outcome: PredictedOutcome): Int = when (outcome) {
        PredictedOutcome.HOME_WIN -> p.homeWinPercent
        PredictedOutcome.AWAY_WIN -> p.awayWinPercent
        PredictedOutcome.DRAW -> p.drawPercent
    }

    companion object {
        private const val MAX_ROWS = 3
        private const val MAX_RESULT_ROWS = 2
        private const val RESULT_WINDOW_DAYS = 2L
        private val KICKOFF_FORMAT = DateTimeFormatter.ofPattern("d MMM · HH:mm")
    }
}

private data class WidgetRow(
    val matchup: String,
    val prediction: String,
    val kickoff: String,
    val edge: Int?,
    val kickoffMillis: LocalDateTime,
    /** Non-null when the match is in play — its current scoreline. */
    val liveScore: String? = null,
    val liveMinute: String = "",
    /** Non-null when the match has finished — its final scoreline. */
    val resultScore: String? = null,
    val resultHit: Boolean = false,
)

// Palette — kept self-contained so the widget doesn't depend on the in-app Compose theme.
private val Ground = Color(0xFF0E1420)
private val Surface = Color(0xFF18202F)
private val Accent = Color(0xFF35C2F5)
private val Live = Color(0xFFE5484D)
private val Hit = Color(0xFF3FB98A)
private val TextPrimary = Color(0xFFF2F5F8)
private val TextSecondary = Color(0xFF9AA7B8)
private val OnAccent = Color(0xFF06121C)

@androidx.compose.runtime.Composable
private fun WidgetContent(rows: List<WidgetRow>) {
    val context = androidx.glance.LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Ground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "KICK",
                style = TextStyle(color = ColorProvider(TextPrimary), fontWeight = FontWeight.Bold, fontSize = 15.sp),
            )
            Text(
                "PREDICT",
                style = TextStyle(color = ColorProvider(Accent), fontWeight = FontWeight.Bold, fontSize = 15.sp),
            )
        }
        if (rows.isEmpty()) {
            Text(
                LocalContextText(R.string.widget_empty),
                style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 13.sp),
            )
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(GlanceModifier.height(6.dp))
                WidgetRowItem(row)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetRowItem(row: WidgetRow) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Surface)
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.matchup,
                style = TextStyle(color = ColorProvider(TextPrimary), fontWeight = FontWeight.Medium, fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (row.liveScore != null) {
                Text(
                    LocalContextText(R.string.live),
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    modifier = GlanceModifier.background(Live).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else if (row.resultScore != null) {
                Text(
                    LocalContextText(if (row.resultHit) R.string.result_hit else R.string.result_miss),
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    modifier = GlanceModifier
                        .background(if (row.resultHit) Hit else Live)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    row.kickoff,
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (row.liveScore != null) {
                // In-play: current score in the live colour, minute alongside.
                Text(
                    row.liveScore,
                    style = TextStyle(color = ColorProvider(Live), fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (row.liveMinute.isNotBlank()) {
                    Text(
                        row.liveMinute,
                        style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp),
                    )
                }
            } else if (row.resultScore != null) {
                // Finished: the final score; the tag above says hit or miss.
                Text(
                    row.resultScore,
                    style = TextStyle(color = ColorProvider(TextPrimary), fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                Text(
                    row.prediction,
                    style = TextStyle(color = ColorProvider(Accent), fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (row.edge != null) {
                    Text(
                        "+${row.edge}%",
                        style = TextStyle(color = ColorProvider(OnAccent), fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        modifier = GlanceModifier
                            .background(Accent)
                            .cornerRadius(8.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Resolve a string resource inside a Glance composable via the local context. */
@androidx.compose.runtime.Composable
private fun LocalContextText(resId: Int): String =
    androidx.glance.LocalContext.current.getString(resId)
