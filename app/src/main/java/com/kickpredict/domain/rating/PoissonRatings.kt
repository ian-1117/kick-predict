package com.kickpredict.domain.rating

import kotlin.math.exp
import kotlin.math.pow

/**
 * A Dixon-Coles–style Poisson attack/defence model, fit **online** (SGD on the Poisson likelihood) so
 * it streams through results walk-forward. Each team carries a log-scale attack and defence rating;
 * expected goals are `λ = exp(base + homeAdv + attack − oppDefence)`. Predictions come from the full
 * Poisson score grid with the Dixon-Coles low-score correlation correction (ρ).
 *
 *  - [homeAwaySplit]: give each team separate home and away attack/defence (captures venue effects) —
 *    the home-advantage term is then folded into the parameters instead of a global constant.
 *  - [regressToMean]: call between seasons to shrink ratings for roster turnover.
 */
class PoissonRatings(
    private val learningRate: Double = 0.06,
    private val homeAdvantage: Double = 0.25,
    private val baseRate: Double = 0.2,
    private val l2: Double = 0.0004,
    private val rho: Double = -0.05,
    private val homeAwaySplit: Boolean = false,
    private val maxGoals: Int = 8,
) {
    // non-split: [attack, defence]; split: [homeAtt, homeDef, awayAtt, awayDef]
    private val ratings = HashMap<String, DoubleArray>()

    private fun paramsOf(id: String) = ratings.getOrPut(id) { DoubleArray(if (homeAwaySplit) 4 else 2) }

    /** Expected goals for (home, away). */
    fun lambdas(home: String, away: String): Pair<Double, Double> {
        val h = paramsOf(home); val a = paramsOf(away)
        val (lh, la) = if (homeAwaySplit) {
            exp(baseRate + h[0] - a[3]) to exp(baseRate + a[2] - h[1])
        } else {
            exp(baseRate + homeAdvantage + h[0] - a[1]) to exp(baseRate + a[0] - h[1])
        }
        return lh.coerceIn(0.05, 6.0) to la.coerceIn(0.05, 6.0)
    }

    /** Win / draw / away-win probabilities from the Poisson grid with the DC low-score correction. */
    fun predict(home: String, away: String): Triple<Double, Double, Double> {
        val (lh, la) = lambdas(home, away)
        var pH = 0.0; var pD = 0.0; var pA = 0.0
        for (i in 0..maxGoals) for (j in 0..maxGoals) {
            val p = (pmf(i, lh) * pmf(j, la) * tau(i, j, lh, la)).coerceAtLeast(0.0)
            when {
                i > j -> pH += p
                i == j -> pD += p
                else -> pA += p
            }
        }
        val s = pH + pD + pA
        return Triple(pH / s, pD / s, pA / s)
    }

    /** One SGD step on the Poisson NLL (gradient wrt a log-mean is λ − goals). */
    fun update(home: String, away: String, homeGoals: Int, awayGoals: Int) {
        val (lh, la) = lambdas(home, away)
        val h = paramsOf(home); val a = paramsOf(away)
        val eHome = lh - homeGoals
        val eAway = la - awayGoals
        if (homeAwaySplit) {
            h[0] -= learningRate * eHome; a[3] += learningRate * eHome
            a[2] -= learningRate * eAway; h[1] += learningRate * eAway
        } else {
            h[0] -= learningRate * eHome; a[1] += learningRate * eHome
            a[0] -= learningRate * eAway; h[1] += learningRate * eAway
        }
        shrink(h); shrink(a)
    }

    /** Shrink every rating toward 0 (call between seasons). */
    fun regressToMean(carryOver: Double) {
        ratings.values.forEach { p -> for (i in p.indices) p[i] *= carryOver }
    }

    private fun shrink(p: DoubleArray) { for (i in p.indices) p[i] *= (1.0 - l2) }

    private fun pmf(k: Int, lambda: Double): Double {
        var fact = 1.0
        for (n in 2..k) fact *= n
        return exp(-lambda) * lambda.pow(k) / fact
    }

    /** Dixon-Coles adjustment for the four low-scoring cells. */
    private fun tau(i: Int, j: Int, lh: Double, la: Double): Double = when {
        i == 0 && j == 0 -> 1.0 - lh * la * rho
        i == 0 && j == 1 -> 1.0 + lh * rho
        i == 1 && j == 0 -> 1.0 + la * rho
        i == 1 && j == 1 -> 1.0 - rho
        else -> 1.0
    }
}

/**
 * A live, swappable Dixon-Coles goal model the engine blends into its λ. Returns null (no effect)
 * until it has been trained and knows *both* teams, so untrained predictions are unchanged.
 */
class MutablePoissonProvider {

    @Volatile
    private var model: PoissonRatings? = null

    @Volatile
    private var known: Set<String> = emptySet()

    /** Learned (home, away) expected goals, or null when untrained or a team is unseen. */
    fun lambdas(homeId: String, awayId: String): Pair<Double, Double>? {
        val m = model ?: return null
        if (homeId !in known || awayId !in known) return null
        return m.lambdas(homeId, awayId)
    }

    fun update(trained: PoissonRatings, teams: Set<String>) {
        model = trained
        known = teams
    }
}
