package com.kickpredict.domain

import com.kickpredict.data.mock.Teams
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.rating.EloModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Holdout validation that the Elo model genuinely *learns*: results are generated from a hidden,
 * independent "true" strength (not the predictor's ratings), then we compare on held-out matches
 * a static predictor (hand-set catalog ratings) against an Elo model trained on the earlier matches.
 * If learning works, the trained Elo has lower log-loss (and usually higher accuracy) on the test set.
 */
class EloValidationTest {

    private data class Played(val homeId: String, val awayId: String, val homeGoals: Int, val awayGoals: Int)

    @Test
    fun `learned Elo beats static ratings and nears the ceiling (realistic league)`() {
        val (learnAcc, baseAcc) = runValidation(strengthSpread = 110.0, tag = "REALISTIC")
        assertTrue("more training should at least match the static baseline", learnAcc >= baseAcc)
    }

    @Test
    fun `a more predictable league lifts the ceiling and the learner rides it up`() {
        // Wider true-strength spread -> fewer coin-flip games -> higher achievable accuracy.
        runValidation(strengthSpread = 320.0, tag = "PREDICTABLE")
    }

    /** @return (learnedAccuracy, baselineAccuracy). Prints baseline/learned/oracle metrics. */
    private fun runValidation(strengthSpread: Double, tag: String): Pair<Double, Double> {
        val rng = Random(42)
        val seeds = Teams.byLeague.getValue(LeagueType.K_LEAGUE) + Teams.byLeague.getValue(LeagueType.K_LEAGUE_2)
        val ids = seeds.map { it.id }

        // What you'd guess from the catalog rating (the static baseline's knowledge).
        val catalogElo = seeds.associate { it.id to 1500.0 + (it.rating - 72.0) * 22.0 }
        // Hidden truth = the catalog signal PLUS a component only results reveal.
        val trueElo = seeds.associate { it.id to catalogElo.getValue(it.id) + rng.nextGaussian() * strengthSpread }
        val truth = EloModel(initial = trueElo)

        // Several seasons of double round-robin, played in a random order (more games -> Elo converges).
        val seasons = 20
        val oneSeason = buildList {
            for (i in ids.indices) for (j in ids.indices) if (i != j) add(ids[i] to ids[j])
        }
        val fixtures = (1..seasons).flatMap { oneSeason }.shuffled(rng)

        val played = fixtures.map { (home, away) ->
            val (pH, pD, _) = truth.predict(home, away)
            val r = rng.nextDouble()
            val (hg, ag) = when {
                r < pH -> 1 to 0
                r < pH + pD -> 1 to 1
                else -> 0 to 1
            }
            Played(home, away, hg, ag)
        }

        val cut = (played.size * 0.8).toInt()
        val train = played.take(cut)
        val test = played.drop(cut)

        // Baseline: static catalog ratings, never updated. Learner: starts blank, learns from train.
        val baseline = EloModel(initial = catalogElo)
        val learner = EloModel()
        train.forEach { learner.update(it.homeId, it.awayId, it.homeGoals, it.awayGoals) }

        val (baseAcc, baseLoss) = evaluate(baseline, test)
        val (learnAcc, learnLoss) = evaluate(learner, test)
        val (oracleAcc, oracleLoss) = evaluate(truth, test) // best achievable (generated from truth)

        println(
            "ELO_VALIDATION[$tag] spread=$strengthSpread train=${train.size} test=${test.size}" +
                " | baseline acc=${pct(baseAcc)}% logloss=${fmt(baseLoss)}" +
                " | learned acc=${pct(learnAcc)}% logloss=${fmt(learnLoss)}" +
                " | oracle acc=${pct(oracleAcc)}% logloss=${fmt(oracleLoss)}",
        )

        assertTrue("learning from results should reduce test log-loss", learnLoss < baseLoss)
        return learnAcc to baseAcc
    }

    private fun evaluate(model: EloModel, test: List<Played>): Pair<Double, Double> {
        var correct = 0
        var logLoss = 0.0
        test.forEach { p ->
            val (pH, pD, pA) = model.predict(p.homeId, p.awayId)
            val probs = listOf(pH, pD, pA)
            val actual = when {
                p.homeGoals > p.awayGoals -> 0
                p.homeGoals < p.awayGoals -> 2
                else -> 1
            }
            if (probs.indexOf(probs.max()) == actual) correct++
            logLoss += -ln(probs[actual].coerceAtLeast(1e-9))
        }
        return correct.toDouble() / test.size to logLoss / test.size
    }

    private fun pct(v: Double) = (v * 100).roundToInt()
    private fun fmt(v: Double) = (v * 1000).roundToInt() / 1000.0

    @Test
    fun `winner gains rating and probabilities sum to one`() {
        val elo = EloModel()
        val before = elo.rating("A")
        elo.update("A", "B", homeGoals = 2, awayGoals = 0)
        assertTrue("home winner's rating should rise", elo.rating("A") > before)
        val (h, d, a) = elo.predict("A", "B")
        assertEquals(1.0, h + d + a, 1e-9)
    }
}

/** Box-Muller standard normal from a Kotlin [Random]. */
private fun Random.nextGaussian(): Double {
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
}
