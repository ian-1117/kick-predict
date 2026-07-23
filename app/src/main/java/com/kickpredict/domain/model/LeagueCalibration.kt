package com.kickpredict.domain.model

/**
 * Data-calibrated parameters that drive the Poisson scoreline model for one league.
 *
 * @param homeGoalsAvg league-average goals scored by the home side per match.
 * @param awayGoalsAvg league-average goals scored by the away side per match.
 * @param drawInflation Dixon-Coles-style multiplier applied to level scorelines to match the
 *        league's observed draw rate (>1 for draw-prone leagues such as Serie A).
 *
 * These are produced either from real historical results ([com.kickpredict.domain.calibration.Calibrator])
 * or the shipped [com.kickpredict.domain.calibration.CalibrationDefaults].
 */
data class LeagueCalibration(
    val homeGoalsAvg: Double,
    val awayGoalsAvg: Double,
    val drawInflation: Double,
) {
    /** League overall mean goals per team, used to normalise a team's own scoring rate. */
    val leagueMeanGoals: Double get() = (homeGoalsAvg + awayGoalsAvg) / 2.0
}

/** Supplies a [LeagueCalibration] for a league; lets the engine stay agnostic to the source. */
fun interface CalibrationProvider {
    fun forLeague(league: LeagueType): LeagueCalibration
}
