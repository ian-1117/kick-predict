package com.kickpredict.domain.engine

import com.kickpredict.domain.calibration.CalibrationDefaults
import com.kickpredict.domain.calibration.ConfidenceCalibration
import com.kickpredict.domain.calibration.IdentityConfidenceCalibration
import com.kickpredict.domain.model.CalibrationProvider
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.rating.MutableEloProvider
import com.kickpredict.domain.rating.MutablePoissonProvider
import com.kickpredict.domain.simulation.ScoreGrid
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Pure-Kotlin, dependency-free prediction engine.
 *
 * Win/draw/away probabilities come from a **Poisson scoreline model**: each side's expected goals
 * (λ) are built from data-calibrated league scoring rates ([calibration]), the teams' own scoring
 * rates, and multiplicative modifiers for quality (rating/position/form), league rules, fixture
 * congestion (fatigue) and the head-to-head **matchup/상성** — then the full score grid is summed.
 *
 * A separate **Confidence Score** ("AI 예상 적중 확률") measures how much to trust the prediction,
 * from data volume, the Poisson win-probability gap, schedule variables and H2H consistency.
 *
 * League rules (per product spec):
 *  - Bundesliga: extra home boost.
 *  - Serie A: elevated draw tendency (via calibrated draw inflation).
 *  - LaLiga: league-position-gap correction (amplified quality gap).
 *  - EPL / K League: fatigue penalty for a team on <=4 days rest.
 *
 * Matchup (상성) is **recency- and venue-weighted**: recent meetings and meetings at this fixture's
 * venue count more, so a bogey team (천적) can swing a match it would lose on paper.
 */
class PredictionEngine(
    private val calibration: CalibrationProvider = CalibrationDefaults,
    private val confidenceCalibration: ConfidenceCalibration = IdentityConfidenceCalibration,
    private val eloProvider: MutableEloProvider = MutableEloProvider(),
    private val poissonProvider: MutablePoissonProvider = MutablePoissonProvider(),
) {

    private companion object {
        const val GOAL_RATIO_EXPONENT = 0.6 // <1 dampens extreme attack/defence ratios
        const val QUALITY_STRENGTH = 0.32 // how much rating/position/form swings the goal ratio
        const val ELO_STRENGTH = 0.30 // how much learned Elo (from real results) skews the goal ratio
        const val POISSON_STRENGTH = 0.45 // blend weight for the learned Dixon-Coles goal model
        const val LALIGA_QUALITY_AMP = 1.25
        const val BUNDESLIGA_HOME_BOOST = 1.10
        const val FATIGUE_PENALTY = 0.88

        const val MATCHUP_STRENGTH = 0.42 // strong enough for a bogey team to flip a paper mismatch
        const val MATCHUP_MIN_GAMES = 2
        const val H2H_RECENCY_DECAY = 0.7
        const val H2H_VENUE_BOOST = 1.6 // same-venue meetings weigh more

        const val LAMBDA_MIN = 0.2
        const val LAMBDA_MAX = 3.2
        const val TOP_SCORELINES = 6

        // Quality index weights (rating/position/form), sums to 1.0.
        const val Q_RATING = 0.45
        const val Q_POSITION = 0.25
        const val Q_FORM = 0.30

        // Confidence signal weights (sum to 1.0) + spread expansion.
        const val C_DATA = 0.25
        const val C_GAP = 0.35
        const val C_SCHEDULE = 0.20
        const val C_H2H = 0.20
        const val CONFIDENCE_EXPANSION = 1.25
    }

    fun predict(match: Match): PredictionResult {
        val league = match.league
        val home = match.homeTeam
        val away = match.awayTeam
        val h2h = match.headToHead
        val cal = calibration.forLeague(league)
        val rationale = mutableListOf<String>()

        // --- 1. Base expected goals from calibrated league rates + team scoring rates ------------
        val mean = cal.leagueMeanGoals
        var lambdaHome = cal.homeGoalsAvg *
            (home.goalsScoredAvg / mean).pow(GOAL_RATIO_EXPONENT) *
            (away.goalsConcededAvg / mean).pow(GOAL_RATIO_EXPONENT)
        var lambdaAway = cal.awayGoalsAvg *
            (away.goalsScoredAvg / mean).pow(GOAL_RATIO_EXPONENT) *
            (home.goalsConcededAvg / mean).pow(GOAL_RATIO_EXPONENT)
        rationale += "λ from ${league.displayName} calibration (home ${fmt(cal.homeGoalsAvg)} / away ${fmt(cal.awayGoalsAvg)})."

        // --- 2. Quality (rating/position/form) skews the goal ratio -----------------------------
        var qualityGap = qualityIndex(home) - qualityIndex(away)
        if (league == LeagueType.LALIGA) {
            qualityGap *= LALIGA_QUALITY_AMP
            rationale += "LaLiga position-gap correction amplifies the quality edge."
        }
        lambdaHome *= (1.0 + QUALITY_STRENGTH * qualityGap)
        lambdaAway *= (1.0 - QUALITY_STRENGTH * qualityGap)

        // --- 3. League + schedule modifiers -----------------------------------------------------
        if (league == LeagueType.BUNDESLIGA) {
            lambdaHome *= BUNDESLIGA_HOME_BOOST
            rationale += "Bundesliga home boost applied to ${home.shortName}."
        }
        if (league == LeagueType.EPL || league == LeagueType.K_LEAGUE || league == LeagueType.K_LEAGUE_2) {
            if (home.isFatigued) {
                lambdaHome *= FATIGUE_PENALTY
                rationale += "${home.shortName} fatigued (${home.daysSinceLastMatch}d rest)."
            }
            if (away.isFatigued) {
                lambdaAway *= FATIGUE_PENALTY
                rationale += "${away.shortName} fatigued (${away.daysSinceLastMatch}d rest)."
            }
        }

        // --- 4. Matchup / 상성 (venue- & recency-weighted) --------------------------------------
        val matchupBias = matchupBias(h2h)
        if (matchupBias != 0.0) {
            lambdaHome *= (1.0 + MATCHUP_STRENGTH * matchupBias)
            lambdaAway *= (1.0 - MATCHUP_STRENGTH * matchupBias)
            rationale += matchupNote(home, away, matchupBias)
        }

        // --- 4a. Learned Elo (from accumulated real results) ------------------------------------
        val eloDiff = eloProvider.ratingDiff(home.id, away.id)
        if (eloDiff != 0.0) {
            val eloSkew = tanh(eloDiff / 400.0) // -1..1
            lambdaHome *= (1.0 + ELO_STRENGTH * eloSkew)
            lambdaAway *= (1.0 - ELO_STRENGTH * eloSkew)
            rationale += "Elo(학습 레이팅) 반영: Δ${eloDiff.roundToInt()}."
        }

        // --- 4a-2. Learned Dixon-Coles attack/defence goal model (from real results) ------------
        poissonProvider.lambdas(home.id, away.id)?.let { (poissonHome, poissonAway) ->
            lambdaHome = (1.0 - POISSON_STRENGTH) * lambdaHome + POISSON_STRENGTH * poissonHome
            lambdaAway = (1.0 - POISSON_STRENGTH) * lambdaAway + POISSON_STRENGTH * poissonAway
            rationale += "실데이터 공수 레이팅(Dixon-Coles) 반영: λ ${fmt(poissonHome)} / ${fmt(poissonAway)}."
        }

        // --- 4b. Context variables: injuries / lineup strength / weather ------------------------
        val context = match.context
        lambdaHome *= context.homeAvailability.availabilityFactor
        lambdaAway *= context.awayAvailability.availabilityFactor
        if (context.homeAvailability.isWeakened) {
            rationale += "${home.shortName} weakened (${context.homeAvailability.keyPlayersInjured} out, ${context.homeAvailability.lineupStrengthPercent}% XI)."
        }
        if (context.awayAvailability.isWeakened) {
            rationale += "${away.shortName} weakened (${context.awayAvailability.keyPlayersInjured} out, ${context.awayAvailability.lineupStrengthPercent}% XI)."
        }
        if (context.weather.isAdverse) {
            lambdaHome *= context.weather.goalFactor
            lambdaAway *= context.weather.goalFactor
            rationale += "${context.weather.displayLabel}: fewer goals expected."
        }

        lambdaHome = lambdaHome.coerceIn(LAMBDA_MIN, LAMBDA_MAX)
        lambdaAway = lambdaAway.coerceIn(LAMBDA_MIN, LAMBDA_MAX)

        // --- 5. Poisson scoreline grid ----------------------------------------------------------
        val grid = ScoreGrid(lambdaHome, lambdaAway, cal.drawInflation)
        val pHome = grid.homeWinProbability
        val pDraw = grid.drawProbability
        val pAway = grid.awayWinProbability
        val (homePct, drawPct, awayPct) = toWholePercents(pHome, pDraw, pAway)

        val topScorelines = grid.topScorelines(TOP_SCORELINES)

        // --- 6. Confidence score ----------------------------------------------------------------
        val formSamples = home.recentForm.take(5).size + away.recentForm.take(5).size
        val dataCompleteness = 0.5 * (formSamples / 10.0) + 0.5 * (h2h.total.coerceAtMost(5) / 5.0)
        val gapScore = abs(pHome - pAway)
        // More live variables (fatigue, injuries, adverse weather) => lower confidence.
        val variableCount = listOf(
            home.isFatigued || away.isFatigued,
            context.homeAvailability.isWeakened || context.awayAvailability.isWeakened,
            context.weather.isAdverse,
        ).count { it }
        val scheduleScore = when (variableCount) {
            0 -> 1.0
            1 -> 0.6
            2 -> 0.4
            else -> 0.25
        }
        val base = 100.0 * (
            C_DATA * dataCompleteness +
                C_GAP * gapScore +
                C_SCHEDULE * scheduleScore +
                C_H2H * h2h.consistency
            )
        val rawConfidence = (50.0 + (base - 50.0) * CONFIDENCE_EXPANSION)
            .roundToInt()
            .coerceIn(5, 97)
        // Re-map against observed hit rate (identity until a reliability curve is fitted).
        val confidenceScore = confidenceCalibration.calibrate(rawConfidence).coerceIn(5, 97)
        // Show the most-likely scoreline that agrees with the predicted result, not the rounded λ —
        // otherwise a 1.2 vs 1.4 game reads "1–1" (a draw) under an "away win" prediction. Falls back
        // to the rounded λ if the top grid cells don't include the predicted outcome.
        val likelyScore = topScorelines.firstOrNull {
            when {
                homePct >= drawPct && homePct >= awayPct -> it.homeGoals > it.awayGoals
                awayPct >= drawPct -> it.awayGoals > it.homeGoals
                else -> it.homeGoals == it.awayGoals
            }
        }?.label ?: "${lambdaHome.roundToInt()} – ${lambdaAway.roundToInt()}"
        rationale += "예상 스코어 $likelyScore (λ ${fmt(lambdaHome)} / ${fmt(lambdaAway)})."

        // Extra markets from the (independent-Poisson) goal expectations.
        val totalLambda = lambdaHome + lambdaAway
        val pUnder = exp(-totalLambda) * (1.0 + totalLambda + totalLambda * totalLambda / 2.0)
        val overPercent = ((1.0 - pUnder) * 100).roundToInt().coerceIn(0, 100)
        val bttsPercent = ((1.0 - exp(-lambdaHome)) * (1.0 - exp(-lambdaAway)) * 100).roundToInt().coerceIn(0, 100)

        return PredictionResult(
            homeWinPercent = homePct,
            drawPercent = drawPct,
            awayWinPercent = awayPct,
            confidenceScore = confidenceScore,
            rawConfidenceScore = rawConfidence,
            rationale = rationale,
            matchupBias = matchupBias,
            expectedHomeGoals = lambdaHome,
            expectedAwayGoals = lambdaAway,
            overProbabilityPercent = overPercent,
            bttsProbabilityPercent = bttsPercent,
            topScorelines = topScorelines,
        )
    }

    /** Intangible quality index 0..1 from rating, table position and recent form. */
    private fun qualityIndex(team: TeamProfile): Double {
        val ratingNorm = (team.overallRating / 100.0).coerceIn(0.0, 1.0)
        val positionNorm = ((21 - team.leaguePosition) / 20.0).coerceIn(0.0, 1.0)
        return Q_RATING * ratingNorm + Q_POSITION * positionNorm + Q_FORM * team.formScore
    }

    private fun matchupBias(h2h: HeadToHead): Double {
        if (h2h.total < MATCHUP_MIN_GAMES) return 0.0
        return h2h.weightedBias(H2H_RECENCY_DECAY, H2H_VENUE_BOOST)
    }

    private fun matchupNote(home: TeamProfile, away: TeamProfile, bias: Double): String {
        val pct = (MATCHUP_STRENGTH * abs(bias) * 100).roundToInt()
        return if (bias > 0) {
            "상성 우위: ${home.shortName} dominates the recent H2H (+$pct% λ)."
        } else {
            "상성 우위: ${away.shortName} is a bogey team (+$pct% λ)."
        }
    }

    private fun fmt(v: Double): String = ((v * 100).roundToInt() / 100.0).toString()

    /**
     * Convert three probabilities (summing to ~1.0) into whole percents that sum to exactly 100,
     * using the largest-remainder method so nothing is silently lost to rounding.
     */
    private fun toWholePercents(home: Double, draw: Double, away: Double): Triple<Int, Int, Int> {
        val raw = listOf(home, draw, away).map { (it * 100).coerceAtLeast(0.0) }
        val floors = raw.map { it.toInt() }.toMutableList()
        var deficit = 100 - floors.sum()
        val remainders = raw.mapIndexed { i, v -> i to (v - floors[i]) }
            .sortedByDescending { it.second }
        var idx = 0
        while (deficit > 0 && remainders.isNotEmpty()) {
            floors[remainders[idx % remainders.size].first]++
            deficit--
            idx++
        }
        return Triple(floors[0], floors[1], floors[2])
    }
}
