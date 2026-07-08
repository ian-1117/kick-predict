package com.kickpredict.domain.model

/**
 * The real final score of a fixture, entered by the user after the match. This is the ground truth
 * the prediction is scored against and the raw material for ongoing calibration.
 */
data class ActualResult(
    val matchId: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val recordedAt: Long,
) {
    val outcome: PredictedOutcome
        get() = when {
            homeGoals > awayGoals -> PredictedOutcome.HOME_WIN
            homeGoals < awayGoals -> PredictedOutcome.AWAY_WIN
            else -> PredictedOutcome.DRAW
        }

    val scoreline: String get() = "$homeGoals – $awayGoals"
}
