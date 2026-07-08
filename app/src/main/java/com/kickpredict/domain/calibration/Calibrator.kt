package com.kickpredict.domain.calibration

import com.kickpredict.domain.model.CalibrationProvider
import com.kickpredict.domain.model.LeagueCalibration
import com.kickpredict.domain.model.LeagueType
import kotlin.math.exp

/** One historical result used to calibrate a league's scoring model. */
data class HistoricalMatch(
    val league: LeagueType,
    val homeGoals: Int,
    val awayGoals: Int,
)

/**
 * Derives [LeagueCalibration] from real historical results via maximum-likelihood averages:
 *  - home/away goal averages are the plain per-match means,
 *  - draw inflation is the observed draw rate divided by the rate an independent Poisson model
 *    would predict at those averages (so a league that draws more than "chance" gets a >1 factor).
 *
 * This is the calibration *pipeline*; when no dataset is available the app falls back to
 * [CalibrationDefaults], which are hand-set to realistic per-league values.
 */
object Calibrator {

    /** Calibrate every league present in [history]; leagues with no data are simply omitted. */
    fun calibrate(history: List<HistoricalMatch>): Map<LeagueType, LeagueCalibration> =
        history.groupBy { it.league }
            .mapValues { (_, games) -> calibrateLeague(games) }

    fun calibrateLeague(games: List<HistoricalMatch>): LeagueCalibration {
        require(games.isNotEmpty()) { "cannot calibrate a league with no games" }
        val n = games.size.toDouble()
        val homeAvg = games.sumOf { it.homeGoals }.toDouble() / n
        val awayAvg = games.sumOf { it.awayGoals }.toDouble() / n
        val observedDrawRate = games.count { it.homeGoals == it.awayGoals } / n
        val impliedDrawRate = impliedDrawRate(homeAvg, awayAvg)
        val inflation = if (impliedDrawRate > 0.0) {
            (observedDrawRate / impliedDrawRate).coerceIn(0.8, 1.6)
        } else 1.0
        return LeagueCalibration(homeAvg, awayAvg, inflation)
    }

    /** Probability of a level scoreline for two independent Poisson goal counts. */
    private fun impliedDrawRate(homeLambda: Double, awayLambda: Double): Double {
        var sum = 0.0
        for (k in 0..MAX_GOALS) sum += poisson(k, homeLambda) * poisson(k, awayLambda)
        return sum
    }

    private const val MAX_GOALS = 10

    internal fun poisson(k: Int, lambda: Double): Double {
        var factorial = 1.0
        for (i in 2..k) factorial *= i
        return exp(-lambda) * Math.pow(lambda, k.toDouble()) / factorial
    }
}

/** Default, realistic per-league calibration used when no historical dataset is supplied. */
object CalibrationDefaults : CalibrationProvider {
    private val table: Map<LeagueType, LeagueCalibration> = mapOf(
        LeagueType.EPL to LeagueCalibration(homeGoalsAvg = 1.52, awayGoalsAvg = 1.22, drawInflation = 1.02),
        LeagueType.BUNDESLIGA to LeagueCalibration(1.62, 1.40, 1.00),
        LeagueType.SERIE_A to LeagueCalibration(1.25, 1.12, 1.28),
        LeagueType.LALIGA to LeagueCalibration(1.44, 1.12, 1.05),
        LeagueType.K_LEAGUE to LeagueCalibration(1.45, 1.20, 1.03),
    )

    override fun forLeague(league: LeagueType): LeagueCalibration = table.getValue(league)
}

/** A [CalibrationProvider] backed by a calibrated map, falling back to defaults for missing leagues. */
class DataCalibration(
    private val calibrated: Map<LeagueType, LeagueCalibration>,
) : CalibrationProvider {
    override fun forLeague(league: LeagueType): LeagueCalibration =
        calibrated[league] ?: CalibrationDefaults.forLeague(league)
}
