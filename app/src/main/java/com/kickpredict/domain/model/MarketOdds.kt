package com.kickpredict.domain.model

import kotlin.math.roundToInt

/**
 * Market 1X2 odds for a fixture (decimal, e.g. 2.10 = a 2.10× payout). The implied probabilities are
 * normalized to remove the bookmaker's overround, giving the market's true view — which the app
 * compares against the model to surface value picks.
 */
data class MarketOdds(
    val homeWin: Double,
    val draw: Double,
    val awayWin: Double,
) {
    private val impliedHome get() = 1.0 / homeWin
    private val impliedDraw get() = 1.0 / draw
    private val impliedAway get() = 1.0 / awayWin
    private val overround get() = impliedHome + impliedDraw + impliedAway

    val homePercent: Int get() = pct(impliedHome)
    val drawPercent: Int get() = pct(impliedDraw)
    val awayPercent: Int get() = pct(impliedAway)

    /** Normalized market probability (0..100) for an outcome. */
    fun percentFor(outcome: PredictedOutcome): Int = when (outcome) {
        PredictedOutcome.HOME_WIN -> homePercent
        PredictedOutcome.DRAW -> drawPercent
        PredictedOutcome.AWAY_WIN -> awayPercent
    }

    fun oddsFor(outcome: PredictedOutcome): Double = when (outcome) {
        PredictedOutcome.HOME_WIN -> homeWin
        PredictedOutcome.DRAW -> draw
        PredictedOutcome.AWAY_WIN -> awayWin
    }

    private fun pct(implied: Double): Int = if (overround <= 0.0) 0 else (implied / overround * 100).roundToInt()

    companion object {
        /** Positive only when all three odds are sensible (> 1.0). */
        fun of(home: Double, draw: Double, away: Double): MarketOdds? =
            if (home > 1.0 && draw > 1.0 && away > 1.0) MarketOdds(home, draw, away) else null
    }
}
