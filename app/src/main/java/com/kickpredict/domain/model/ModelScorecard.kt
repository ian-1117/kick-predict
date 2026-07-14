package com.kickpredict.domain.model

import kotlin.math.ln

/**
 * One settled match's full predicted distribution vs what happened — the input to proper scoring
 * rules (which need the whole 1X2 probability vector, not just the picked outcome).
 */
data class ScoringSample(
    val homeWinPercent: Int,
    val drawPercent: Int,
    val awayWinPercent: Int,
    val actual: PredictedOutcome,
    /** Which league this settled prediction belongs to — used for the per-league scorecard. */
    val league: LeagueType = LeagueType.EPL,
    /** When the result was recorded — orders samples for the drift-over-time view. */
    val recordedAt: Long = 0L,
)

/**
 * Proper-scoring summary of the model's predictions. Unlike hit rate, these reward *sharp and
 * honest* probabilities and punish overconfidence, so they separate "right for the right reason"
 * from lucky calls.
 *
 * @param brierScore mean squared error of the probability vector (0 best, 2 worst).
 * @param logLoss mean negative log-likelihood of the actual outcome (0 best; unbounded when confident-and-wrong).
 * @param calibrationError expected calibration error — how far predicted confidence sits from actual hit rate (0 best, 1 worst).
 */
data class ModelScorecard(
    val brierScore: Double,
    val logLoss: Double,
    val calibrationError: Double,
    val sampleCount: Int,
)

/**
 * One chronological slice of the settled history and how sharp the model was over it. Comparing the
 * newest window's [brierScore] to the oldest shows whether the model is improving or drifting.
 */
data class DriftWindow(
    val index: Int, // 0 = oldest
    val sampleCount: Int,
    val brierScore: Double,
)

/**
 * Split the settled samples into up to [maxWindows] equal chronological windows (oldest → newest) and
 * score each with Brier. Returns empty when there isn't enough history to form at least two windows of
 * [minPerWindow], since a one-window "trend" says nothing. Lower Brier = sharper.
 */
fun brierDrift(
    samples: List<ScoringSample>,
    maxWindows: Int = 6,
    minPerWindow: Int = 10,
): List<DriftWindow> {
    val ordered = samples.sortedBy { it.recordedAt }
    val n = ordered.size
    val windowCount = minOf(maxWindows, n / minPerWindow)
    if (windowCount < 2) return emptyList()
    val size = n / windowCount
    return (0 until windowCount).mapNotNull { w ->
        val start = w * size
        val end = if (w == windowCount - 1) n else start + size
        val brier = computeScorecard(ordered.subList(start, end))?.brierScore ?: return@mapNotNull null
        DriftWindow(index = w, sampleCount = end - start, brierScore = brier)
    }
}

/** Compute the scorecard from settled samples; null when there's nothing to score. */
fun computeScorecard(samples: List<ScoringSample>): ModelScorecard? {
    if (samples.isEmpty()) return null

    var brierSum = 0.0
    var logLossSum = 0.0
    // Confidence-vs-accuracy bins (deciles of the predicted outcome's probability) for calibration error.
    val binCounts = IntArray(10)
    val binConfSum = DoubleArray(10)
    val binCorrect = IntArray(10)

    samples.forEach { s ->
        val raw = doubleArrayOf(s.homeWinPercent / 100.0, s.drawPercent / 100.0, s.awayWinPercent / 100.0)
        val sum = raw.sum().takeIf { it > 0.0 } ?: 1.0
        val p = DoubleArray(3) { raw[it] / sum } // normalise rounding drift to a proper distribution
        val actualIdx = when (s.actual) {
            PredictedOutcome.HOME_WIN -> 0
            PredictedOutcome.DRAW -> 1
            PredictedOutcome.AWAY_WIN -> 2
        }

        // Brier: squared error against the one-hot outcome.
        for (k in 0..2) {
            val y = if (k == actualIdx) 1.0 else 0.0
            brierSum += (p[k] - y) * (p[k] - y)
        }
        // Log-loss on the actual outcome's probability (clamped away from 0).
        logLossSum += -ln(p[actualIdx].coerceIn(1e-4, 1.0))

        // Calibration: bin by the model's confidence (its max probability).
        val predIdx = (0..2).maxBy { p[it] }
        val conf = p[predIdx]
        val bin = (conf * 10).toInt().coerceIn(0, 9)
        binCounts[bin]++
        binConfSum[bin] += conf
        if (predIdx == actualIdx) binCorrect[bin]++
    }

    val n = samples.size
    val ece = (0..9).sumOf { b ->
        if (binCounts[b] == 0) 0.0 else {
            val avgConf = binConfSum[b] / binCounts[b]
            val accuracy = binCorrect[b].toDouble() / binCounts[b]
            binCounts[b].toDouble() / n * kotlin.math.abs(avgConf - accuracy)
        }
    }

    return ModelScorecard(
        brierScore = brierSum / n,
        logLoss = logLossSum / n,
        calibrationError = ece,
        sampleCount = n,
    )
}
