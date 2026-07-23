package com.kickpredict.domain.model

import kotlin.math.roundToInt

/** Half-Kelly, capped at a quarter of the bankroll — the same tame-the-variance defaults the ledger uses. */
private const val ACCA_KELLY_FRACTION = 0.5
private const val ACCA_MAX_STAKE_FRACTION = 0.25

/**
 * A parlay built from several value picks. Legs are assumed independent, so the combined price is the
 * product of the leg odds and the joint probability is the product of the per-leg probabilities — the
 * standard accumulator maths. The edge is what the model thinks the parlay is worth versus what the
 * market is pricing it at; the Kelly stake sizes the bet against that edge.
 *
 * @param comboOdds decimal odds of all legs landing together (product of leg odds).
 * @param jointModelProb model's probability all legs land, 0..1.
 * @param jointMarketProb market's implied probability all legs land, 0..1.
 * @param edgePercent model probability minus market probability, in whole points (can be negative).
 * @param kellyStakeFraction fraction of bankroll to stake, half-Kelly and capped, 0..0.25.
 */
data class AccumulatorSummary(
    val legCount: Int,
    val comboOdds: Double,
    val jointModelProb: Double,
    val jointMarketProb: Double,
    val edgePercent: Int,
    val kellyStakeFraction: Double,
) {
    /** Joint model probability as a whole percent, for display. */
    val jointModelPercent: Int get() = (jointModelProb * 100).roundToInt()

    /** Stake as a whole percent of the bankroll, for display. */
    val kellyStakePercent: Int get() = (kellyStakeFraction * 100).roundToInt()
}

/**
 * Combine [legs] into a parlay, or null when there are fewer than two (a parlay needs at least two).
 * Each leg's model probability is reconstructed from its price and edge — market implied (1 / odds)
 * plus the logged edge — so both the joint model and joint market probabilities come off the same
 * odds, keeping the parlay edge internally consistent.
 */
fun buildAccumulator(legs: List<ValuePick>): AccumulatorSummary? {
    if (legs.size < 2) return null

    var comboOdds = 1.0
    var jointModel = 1.0
    var jointMarket = 1.0
    legs.forEach { leg ->
        val marketProb = if (leg.odds > 0.0) (1.0 / leg.odds).coerceIn(0.0, 1.0) else 0.0
        val modelProb = (marketProb + leg.edge / 100.0).coerceIn(0.0, 1.0)
        comboOdds *= leg.odds
        jointMarket *= marketProb
        jointModel *= modelProb
    }

    // Standard Kelly on the parlay as a single bet: f* = (b·p − q) / b, then halved and capped.
    val b = comboOdds - 1.0
    val p = jointModel
    val q = 1.0 - p
    val fullKelly = if (b <= 0.0) 0.0 else (b * p - q) / b
    val kelly = (fullKelly * ACCA_KELLY_FRACTION).coerceIn(0.0, ACCA_MAX_STAKE_FRACTION)

    return AccumulatorSummary(
        legCount = legs.size,
        comboOdds = comboOdds,
        jointModelProb = jointModel,
        jointMarketProb = jointMarket,
        edgePercent = ((jointModel - jointMarket) * 100).roundToInt(),
        kellyStakeFraction = kelly,
    )
}
