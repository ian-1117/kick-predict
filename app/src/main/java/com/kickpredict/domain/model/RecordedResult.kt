package com.kickpredict.domain.model

/**
 * A recorded fixture result joined with the prediction that was made for it — the unit shown in the
 * calibration dashboard and used to score the engine.
 */
data class RecordedResult(
    val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val predictedOutcome: PredictedOutcome,
    val confidence: Int,
    val homeGoals: Int,
    val awayGoals: Int,
    val recordedAt: Long,
) {
    val actualOutcome: PredictedOutcome
        get() = when {
            homeGoals > awayGoals -> PredictedOutcome.HOME_WIN
            homeGoals < awayGoals -> PredictedOutcome.AWAY_WIN
            else -> PredictedOutcome.DRAW
        }

    val wasCorrect: Boolean get() = predictedOutcome == actualOutcome

    val scoreline: String get() = "$homeGoals – $awayGoals"
}
