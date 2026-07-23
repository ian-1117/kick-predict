package com.kickpredict.domain.model

import kotlin.math.roundToInt

/** Even blend of the model with the market — the weight the backtest found improves calibration. */
const val LIVE_BLEND_WEIGHT = 0.5

/**
 * Temper the engine's 1X2 probabilities toward the market's overround-free price. The backtest showed
 * a model–market blend beats either the model alone (better proper scores) — the market is a strong
 * prior worth learning from. Only the win/draw/away split is blended (predicted outcome and the
 * consistent scoreline follow from it); the confidence and Poisson goal expectations are left as the
 * model's. A [RationaleNote.MarketBlend] is appended so the tempering is transparent.
 *
 * @param modelWeight weight on the model in 0..1; the market gets the remainder.
 */
fun PredictionResult.blendedWithMarket(odds: MarketOdds, modelWeight: Double = LIVE_BLEND_WEIGHT): PredictionResult {
    val marketWeight = 1.0 - modelWeight
    val blended = doubleArrayOf(
        modelWeight * homeWinPercent + marketWeight * odds.homePercent,
        modelWeight * drawPercent + marketWeight * odds.drawPercent,
        modelWeight * awayWinPercent + marketWeight * odds.awayPercent,
    )
    val (h, d, a) = roundToHundred(blended)
    return copy(
        homeWinPercent = h,
        drawPercent = d,
        awayWinPercent = a,
        rationale = rationale + RationaleNote.MarketBlend((marketWeight * 100).roundToInt()),
    )
}

/** Round three shares to whole percents that still sum to 100 (largest-remainder method). */
private fun roundToHundred(values: DoubleArray): Triple<Int, Int, Int> {
    val floors = values.map { it.toInt() }
    var remainder = 100 - floors.sum()
    val order = values.indices.sortedByDescending { values[it] - floors[it] }
    val result = floors.toIntArray()
    var i = 0
    while (remainder > 0 && i < order.size) {
        result[order[i]]++
        remainder--
        i++
    }
    return Triple(result[0], result[1], result[2])
}
