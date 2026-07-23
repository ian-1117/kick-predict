package com.kickpredict.data

import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * "Honest mode" accuracy: the demo dials are switched OFF. Instead of forcing the actual outcome to
 * match the prediction ([com.kickpredict.data.mock.ResultSimulator]'s MODEL_FIDELITY) or amplifying
 * the goal gap (SEPARATION), we sample each scoreline straight from the engine's own **raw** expected
 * goals λ. The resulting accuracy is the genuine self-consistency of the model — what you'd expect on
 * real 1X2 outcomes — rather than a tuned demo figure. Printed to the test XML for inspection.
 */
class HonestAccuracyTest {

    private val rounds = 100
    private val samplesPerFixture = 20

    @Test
    fun `honest accuracy without the demo dial`() {
        val engine = PredictionEngine()
        var totalCorrect = 0
        var total = 0
        var homeWins = 0
        val perLeague = StringBuilder()

        LeagueType.entries.forEach { league ->
            val fixtures = MockDataProvider.leagueSchedule(league, rounds = rounds)
                .map { it.copy(predictedResult = engine.predict(it)) }
            var correct = 0
            var n = 0
            fixtures.forEach { match ->
                val p = match.predictedResult!!
                val rng = Random(("honest:" + match.id).hashCode())
                repeat(samplesPerFixture) {
                    val home = samplePoisson(rng, p.expectedHomeGoals)
                    val away = samplePoisson(rng, p.expectedAwayGoals)
                    val actual = outcomeOf(home, away)
                    if (actual == p.predictedOutcome) correct++
                    if (actual == PredictedOutcome.HOME_WIN) homeWins++
                    n++
                }
            }
            totalCorrect += correct
            total += n
            perLeague.append(" ${league.name}=${pct(correct, n)}%")
        }

        println(
            "HONEST_ACCURACY overall=${pct(totalCorrect, total)}%" +
                " homeWinBaseline=${pct(homeWins, total)}% samples=$total |$perLeague",
        )
        assertTrue(total > 0)
    }

    private fun outcomeOf(home: Int, away: Int): PredictedOutcome = when {
        home > away -> PredictedOutcome.HOME_WIN
        home < away -> PredictedOutcome.AWAY_WIN
        else -> PredictedOutcome.DRAW
    }

    private fun pct(correct: Int, total: Int) = (correct.toDouble() / total * 100).roundToInt()

    /** Knuth's Poisson sampler on the raw λ (no amplification). */
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
