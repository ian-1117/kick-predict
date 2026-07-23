package com.kickpredict.data.mock

import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictedOutcome
import kotlin.math.exp
import kotlin.random.Random

/**
 * Generates a plausible **actual** scoreline for a fixture, used to seed sample results so the
 * calibration loop and accuracy display have data without hand-entering hundreds of games.
 *
 * The scoreline magnitude comes from the model's expected goals (amplified so favourites separate),
 * while the *outcome* matches the prediction [MODEL_FIDELITY] of the time — i.e. we simulate a world
 * where the engine is right ~80% of the time, and wrong (an upset) the rest. Deterministic per match.
 */
object ResultSimulator {

    // >1: bigger goal gap between the stronger and weaker side (realistic scorelines).
    private const val SEPARATION = 1.35

    // Share of fixtures whose actual outcome equals the predicted outcome -> target accuracy.
    private const val MODEL_FIDELITY = 0.80

    fun simulate(match: Match): Pair<Int, Int> {
        val prediction = match.predictedResult ?: return 0 to 0
        val rng = Random(("result:" + match.id).hashCode())

        val mean = (prediction.expectedHomeGoals + prediction.expectedAwayGoals) / 2.0
        val lambdaHome = mean + (prediction.expectedHomeGoals - mean) * SEPARATION
        val lambdaAway = mean + (prediction.expectedAwayGoals - mean) * SEPARATION
        var home = samplePoisson(rng, lambdaHome)
        var away = samplePoisson(rng, lambdaAway)

        // Decide the outcome: mostly the prediction, occasionally an upset — then make the score fit.
        val target = if (rng.nextDouble() < MODEL_FIDELITY) prediction.predictedOutcome
        else PredictedOutcome.entries.filter { it != prediction.predictedOutcome }.random(rng)
        when (target) {
            PredictedOutcome.HOME_WIN -> if (home <= away) home = away + 1
            PredictedOutcome.AWAY_WIN -> if (away <= home) away = home + 1
            PredictedOutcome.DRAW -> away = home
        }

        return home.coerceIn(0, 9) to away.coerceIn(0, 9)
    }

    /** Knuth's algorithm for sampling a Poisson(λ) count. */
    private fun samplePoisson(rng: Random, lambda: Double): Int {
        val l = exp(-lambda.coerceIn(0.05, 6.0))
        var k = 0
        var p = 1.0
        do {
            k++
            p *= rng.nextDouble()
        } while (p > l)
        return (k - 1).coerceAtMost(9)
    }
}
