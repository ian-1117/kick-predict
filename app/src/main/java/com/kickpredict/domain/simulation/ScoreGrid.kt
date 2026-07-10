package com.kickpredict.domain.simulation

import com.kickpredict.domain.model.ScoreLine
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The joint distribution over final scorelines for one fixture: independent Poisson goal counts for
 * each side, with level scorelines scaled by the league's Dixon-Coles [drawInflation].
 *
 * This is the single source of truth for "what might this match finish as" — the
 * [com.kickpredict.domain.engine.PredictionEngine] sums it into win/draw/away probabilities, and
 * [SeasonSimulator] samples from it. Keeping one implementation means a simulated season is drawn
 * from exactly the distribution the engine reports, not an approximation of it.
 *
 * Cells are held sorted by descending probability so [sample] terminates after a few steps.
 */
class ScoreGrid(
    lambdaHome: Double,
    lambdaAway: Double,
    drawInflation: Double,
    maxGoals: Int = MAX_GOALS,
) {
    private val homeGoals: IntArray
    private val awayGoals: IntArray

    /** Running sum of the sorted cell probabilities; last entry is 1.0. */
    private val cumulative: DoubleArray

    val homeWinProbability: Double
    val drawProbability: Double
    val awayWinProbability: Double

    init {
        val size = (maxGoals + 1) * (maxGoals + 1)
        val h = IntArray(size)
        val a = IntArray(size)
        val p = DoubleArray(size)

        var home = 0.0
        var draw = 0.0
        var away = 0.0
        var norm = 0.0
        var n = 0
        for (i in 0..maxGoals) {
            for (j in 0..maxGoals) {
                val base = poisson(i, lambdaHome) * poisson(j, lambdaAway)
                val effective = if (i == j) base * drawInflation else base
                h[n] = i
                a[n] = j
                p[n] = effective
                n++
                norm += effective
                when {
                    i > j -> home += base
                    i == j -> draw += effective
                    else -> away += base
                }
            }
        }

        homeWinProbability = home / norm
        drawProbability = draw / norm
        awayWinProbability = away / norm

        val order = (0 until size).sortedByDescending { p[it] }
        homeGoals = IntArray(size) { h[order[it]] }
        awayGoals = IntArray(size) { a[order[it]] }
        cumulative = DoubleArray(size)
        var running = 0.0
        for (k in 0 until size) {
            running += p[order[k]] / norm
            cumulative[k] = running
        }
        cumulative[size - 1] = 1.0 // guard against float drift so sample() always lands
    }

    /** Draw one final score from the distribution. */
    fun sample(random: Random): Int {
        val target = random.nextDouble()
        var k = 0
        while (k < cumulative.size - 1 && cumulative[k] < target) k++
        return pack(homeGoals[k], awayGoals[k])
    }

    /** The [count] most likely scorelines, most likely first. */
    fun topScorelines(count: Int): List<ScoreLine> {
        val result = ArrayList<ScoreLine>(count)
        var previous = 0.0
        for (k in 0 until minOf(count, cumulative.size)) {
            val probability = ((cumulative[k] - previous) * 100).roundToInt()
            previous = cumulative[k]
            if (probability > 0) result += ScoreLine(homeGoals[k], awayGoals[k], probability)
        }
        return result
    }

    private fun poisson(k: Int, lambda: Double): Double {
        var factorial = 1.0
        for (i in 2..k) factorial *= i
        return exp(-lambda) * lambda.pow(k) / factorial
    }

    companion object {
        const val MAX_GOALS = 8

        /** Samples are returned packed so the hot loop never allocates a Pair. */
        fun pack(home: Int, away: Int): Int = home shl 8 or away
        fun unpackHome(packed: Int): Int = packed shr 8
        fun unpackAway(packed: Int): Int = packed and 0xFF
    }
}
