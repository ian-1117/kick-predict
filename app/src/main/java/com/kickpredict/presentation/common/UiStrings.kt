package com.kickpredict.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kickpredict.R
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.Weather

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
