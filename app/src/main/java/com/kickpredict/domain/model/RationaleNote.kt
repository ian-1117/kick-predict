package com.kickpredict.domain.model

/**
 * One driver behind a prediction, kept **structured** (not a formatted string) so the presentation
 * layer can localise it — the engine has no Context. The UI maps each note to a string resource.
 */
sealed interface RationaleNote {
    data class Calibration(val league: LeagueType, val homeAvg: Double, val awayAvg: Double) : RationaleNote
    data object LaLigaPositionGap : RationaleNote
    data class BundesligaHomeBoost(val team: String) : RationaleNote
    data class Fatigue(val team: String, val days: Int) : RationaleNote
    /** [dominates] true: the home side owns the recent H2H; false: the away side is a bogey team. */
    data class Matchup(val team: String, val percent: Int, val dominates: Boolean) : RationaleNote
    data class Elo(val delta: Int) : RationaleNote
    data class Poisson(val homeLambda: Double, val awayLambda: Double) : RationaleNote
    data class Weakened(val team: String, val out: Int, val xiPercent: Int) : RationaleNote
    data class Weather(val weather: com.kickpredict.domain.model.Weather) : RationaleNote
    data class ExpectedScore(val scoreline: String, val homeLambda: Double, val awayLambda: Double) : RationaleNote

    /** The engine's probabilities were tempered toward the market's price by [marketWeightPercent]. */
    data class MarketBlend(val marketWeightPercent: Int) : RationaleNote
}
