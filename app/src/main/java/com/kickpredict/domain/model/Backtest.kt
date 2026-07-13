package com.kickpredict.domain.model

import java.time.LocalDate

/**
 * One historical match with its real result and (for leagues that have them) the market's average
 * and closing 1X2 odds — the raw material for a walk-forward backtest.
 */
data class BacktestGame(
    val league: LeagueType,
    val date: LocalDate,
    val homeId: String,
    val awayId: String,
    val homeGoals: Int,
    val awayGoals: Int,
    /** Average market price (the price a bet is "taken" at); null for leagues without odds. */
    val avgOdds: MarketOdds?,
    /** Closing market price, for closing-line value; null for leagues without odds. */
    val closeOdds: MarketOdds?,
) {
    val actual: PredictedOutcome
        get() = when {
            homeGoals > awayGoals -> PredictedOutcome.HOME_WIN
            homeGoals < awayGoals -> PredictedOutcome.AWAY_WIN
            else -> PredictedOutcome.DRAW
        }
}

/**
 * Backtest metrics for one league (or the whole set when [league] is null): out-of-sample accuracy,
 * proper scores, and — over leagues with odds — flat-stake value-betting ROI and closing-line value.
 */
data class BacktestLeagueResult(
    val league: LeagueType?,
    val games: Int,
    val correct: Int,
    val brier: Double,
    val logLoss: Double,
    val bets: Int,
    val betsWon: Int,
    val roiPercent: Int,
    val clvTenths: Int,
) {
    val accuracy: Double get() = if (games == 0) 0.0 else correct.toDouble() / games
}

/** The full backtest: an overall row plus a per-league breakdown. */
data class BacktestReport(
    val overall: BacktestLeagueResult,
    val byLeague: List<BacktestLeagueResult>,
)
