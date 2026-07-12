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
        val odds = runCatching { container.odds() }.getOrDefault(emptyMap())
        val now = LocalDateTime.now()
        return matches
            .filter { it.predictedResult != null && it.kickoff.isAfter(now) }
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
            .take(MAX_ROWS)
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
        private val KICKOFF_FORMAT = DateTimeFormatter.ofPattern("d MMM · HH:mm")
    }
}

private data class WidgetRow(
    val matchup: String,
    val prediction: String,
    val kickoff: String,
    val edge: Int?,
    val kickoffMillis: LocalDateTime,
)

// Palette — kept self-contained so the widget doesn't depend on the in-app Compose theme.
private val Ground = Color(0xFF0E1420)
private val Surface = Color(0xFF18202F)
private val Accent = Color(0xFF35C2F5)
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
            Text(
                row.kickoff,
                style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

/** Resolve a string resource inside a Glance composable via the local context. */
@androidx.compose.runtime.Composable
private fun LocalContextText(resId: Int): String =
    androidx.glance.LocalContext.current.getString(resId)
