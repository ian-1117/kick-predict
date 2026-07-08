package com.kickpredict.domain.rating

import kotlin.math.abs
import kotlin.math.pow

/**
 * A self-updating Elo rating model that learns team strength from actual results — a genuinely
 * data-driven predictor (as opposed to the hand-tuned catalog ratings). Ratings move after every
 * match toward whatever the results imply, so with enough games it discovers each team's true
 * strength and produces 1X2 probabilities from the rating gap.
 *
 * @param kFactor how fast ratings adapt per match.
 * @param homeAdvantage rating points added to the home side.
 * @param drawBase peak draw probability (for evenly-rated sides).
 * @param baseRating starting rating for unseen teams.
 * @param initial optional seed ratings (e.g. a prior guess) to start from.
 */
class EloModel(
    private val kFactor: Double = 24.0,
    private val homeAdvantage: Double = 60.0,
    private val drawBase: Double = 0.30,
    private val baseRating: Double = 1500.0,
    initial: Map<String, Double> = emptyMap(),
) {
    private val ratings: MutableMap<String, Double> = initial.toMutableMap()

    fun rating(teamId: String): Double = ratings.getOrDefault(teamId, baseRating)

    /** Home team's expected score in [0,1] (win=1, draw=0.5, loss=0). */
    fun homeExpectancy(homeId: String, awayId: String): Double {
        val diff = rating(homeId) + homeAdvantage - rating(awayId)
        return 1.0 / (1.0 + 10.0.pow(-diff / 400.0))
    }

    /** Win / draw / away-win probabilities derived from the rating gap. */
    fun predict(homeId: String, awayId: String): Triple<Double, Double, Double> {
        val we = homeExpectancy(homeId, awayId)
        val pDraw = (drawBase * (1.0 - 2.0 * abs(we - 0.5))).coerceIn(0.02, 0.40)
        val pHome = (we - 0.5 * pDraw).coerceAtLeast(0.0)
        val pAway = (1.0 - we - 0.5 * pDraw).coerceAtLeast(0.0)
        val sum = pHome + pDraw + pAway
        return Triple(pHome / sum, pDraw / sum, pAway / sum)
    }

    /** Update both teams' ratings after a played match. */
    fun update(homeId: String, awayId: String, homeGoals: Int, awayGoals: Int) {
        val expected = homeExpectancy(homeId, awayId)
        val actual = when {
            homeGoals > awayGoals -> 1.0
            homeGoals < awayGoals -> 0.0
            else -> 0.5
        }
        val delta = kFactor * (actual - expected)
        ratings[homeId] = rating(homeId) + delta
        ratings[awayId] = rating(awayId) - delta
    }
}

/**
 * A live, swappable Elo model the engine reads on every prediction. Starts empty (all teams at the
 * base rating → zero effect), and is replaced with a freshly trained model as results accumulate.
 */
class MutableEloProvider(initial: EloModel = EloModel()) {

    @Volatile
    private var model: EloModel = initial

    /** Learned rating gap (home − away); 0 while untrained, so predictions are unchanged. */
    fun ratingDiff(homeId: String, awayId: String): Double = model.rating(homeId) - model.rating(awayId)

    fun update(trained: EloModel) {
        model = trained
    }
}

