package com.kickpredict.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RationaleNote
import com.kickpredict.domain.model.Weather
import kotlin.math.roundToInt

/** Localised generic name for a predicted outcome (home win / away win / draw). */
@Composable
fun outcomeName(outcome: PredictedOutcome): String = when (outcome) {
    PredictedOutcome.HOME_WIN -> stringResource(R.string.outcome_home)
    PredictedOutcome.AWAY_WIN -> stringResource(R.string.outcome_away)
    PredictedOutcome.DRAW -> stringResource(R.string.outcome_draw)
}

/** Localised weather label. */
@Composable
fun weatherName(weather: Weather): String = when (weather) {
    Weather.CLEAR -> stringResource(R.string.weather_clear)
    Weather.RAIN -> stringResource(R.string.weather_rain)
    Weather.WIND -> stringResource(R.string.weather_wind)
    Weather.SNOW -> stringResource(R.string.weather_snow)
    Weather.HEAT -> stringResource(R.string.weather_heat)
}

/** Localised confidence-tier label (Very high … Very low). */
@Composable
fun confidenceTierName(tier: ConfidenceTier): String = when (tier) {
    ConfidenceTier.VERY_HIGH -> stringResource(R.string.tier_very_high)
    ConfidenceTier.HIGH -> stringResource(R.string.tier_high)
    ConfidenceTier.MODERATE -> stringResource(R.string.tier_moderate)
    ConfidenceTier.LOW -> stringResource(R.string.tier_low)
    ConfidenceTier.VERY_LOW -> stringResource(R.string.tier_very_low)
}

/** Localised text for one prediction-rationale note. */
@Composable
fun rationaleText(note: RationaleNote): String = when (note) {
    is RationaleNote.Calibration ->
        stringResource(R.string.rationale_calibration, note.league.displayName, lambda(note.homeAvg), lambda(note.awayAvg))
    RationaleNote.LaLigaPositionGap -> stringResource(R.string.rationale_laliga_gap)
    is RationaleNote.BundesligaHomeBoost -> stringResource(R.string.rationale_bundesliga_boost, note.team)
    is RationaleNote.Fatigue -> stringResource(R.string.rationale_fatigue, note.team, note.days)
    is RationaleNote.Matchup ->
        stringResource(if (note.dominates) R.string.rationale_matchup_dominates else R.string.rationale_matchup_bogey, note.team, note.percent)
    is RationaleNote.Elo -> stringResource(R.string.rationale_elo, note.delta)
    is RationaleNote.Poisson -> stringResource(R.string.rationale_poisson, lambda(note.homeLambda), lambda(note.awayLambda))
    is RationaleNote.Weakened -> stringResource(R.string.rationale_weakened, note.team, note.out, note.xiPercent)
    is RationaleNote.Weather -> stringResource(R.string.rationale_weather, weatherName(note.weather))
    is RationaleNote.ExpectedScore ->
        stringResource(R.string.rationale_expected_score, note.scoreline, lambda(note.homeLambda), lambda(note.awayLambda))
    is RationaleNote.MarketBlend -> stringResource(R.string.rationale_market_blend, note.marketWeightPercent)
}

private fun lambda(v: Double): String = ((v * 100).roundToInt() / 100.0).toString()
