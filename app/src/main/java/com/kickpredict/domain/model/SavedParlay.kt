package com.kickpredict.domain.model

import kotlin.math.roundToInt

/** One leg of a saved parlay: the pick and the price it was taken at. */
data class ParlayLeg(
    val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val pickedOutcome: PredictedOutcome,
    val odds: Double,
)

/**
 * A parlay the user saved from the accumulator builder. The combined price / edge / stake are frozen
 * at save time; whether it landed is derived from results, not stored.
 */
data class SavedParlay(
    val id: String,
    val legs: List<ParlayLeg>,
    val comboOdds: Double,
    val edgePercent: Int,
    val kellyStakeFraction: Double,
    val createdAt: Long,
) {
    val kellyStakePercent: Int get() = (kellyStakeFraction * 100).roundToInt()
}

/** A parlay's settlement state: it wins only if every leg wins. */
enum class ParlayStatus { PENDING, WON, LOST }

/** A leg joined against the recorded result. */
data class SettledParlayLeg(val leg: ParlayLeg, val status: ValuePickStatus)

/** A saved parlay with each leg settled and the parlay's overall outcome + profit on a 1-unit stake. */
data class SettledParlay(
    val parlay: SavedParlay,
    val legs: List<SettledParlayLeg>,
    val status: ParlayStatus,
) {
    /** Profit on a 1-unit stake: the full combined return if it landed, −1 if any leg missed, 0 while open. */
    val profit: Double
        get() = when (status) {
            ParlayStatus.WON -> parlay.comboOdds - 1.0
            ParlayStatus.LOST -> -1.0
            ParlayStatus.PENDING -> 0.0
        }
}

/**
 * Settle one parlay against a map of match id → actual outcome. A leg with no result yet is pending;
 * the parlay loses the moment any leg loses, is pending while any leg is still open (and none lost),
 * and wins only when every leg has won.
 */
fun settleParlay(parlay: SavedParlay, actuals: Map<String, PredictedOutcome>): SettledParlay {
    val legs = parlay.legs.map { leg ->
        val actual = actuals[leg.matchId]
        val status = when {
            actual == null -> ValuePickStatus.PENDING
            actual == leg.pickedOutcome -> ValuePickStatus.WON
            else -> ValuePickStatus.LOST
        }
        SettledParlayLeg(leg, status)
    }
    val status = when {
        legs.any { it.status == ValuePickStatus.LOST } -> ParlayStatus.LOST
        legs.any { it.status == ValuePickStatus.PENDING } -> ParlayStatus.PENDING
        else -> ParlayStatus.WON
    }
    return SettledParlay(parlay, legs, status)
}

/** The saved-parlays ledger rolled up: each settled parlay (newest first) plus ROI over the settled ones. */
data class ParlaysReport(
    val parlays: List<SettledParlay> = emptyList(),
    val settledCount: Int = 0,
    val wonCount: Int = 0,
    val roiPercent: Int = 0,
) {
    val totalCount: Int get() = parlays.size
    val pendingCount: Int get() = parlays.count { it.status == ParlayStatus.PENDING }
    val lostCount: Int get() = settledCount - wonCount
}

/** Settle every saved parlay against results (newest first) and tally ROI on flat 1-unit stakes. */
fun buildParlaysReport(parlays: List<SavedParlay>, actuals: Map<String, PredictedOutcome>): ParlaysReport {
    val settled = parlays
        .sortedByDescending { it.createdAt }
        .map { settleParlay(it, actuals) }
    val decided = settled.filter { it.status != ParlayStatus.PENDING }
    val roi = if (decided.isEmpty()) 0
    else (decided.sumOf { it.profit } / decided.size * 100).roundToInt()
    return ParlaysReport(
        parlays = settled,
        settledCount = decided.size,
        wonCount = decided.count { it.status == ParlayStatus.WON },
        roiPercent = roi,
    )
}
