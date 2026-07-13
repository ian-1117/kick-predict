package com.kickpredict.domain.model

import kotlin.math.roundToInt

/** Half-Kelly staking, capped at a quarter of the bankroll — the standard tame-the-variance defaults. */
private const val KELLY_FRACTION = 0.5
private const val MAX_STAKE_FRACTION = 0.25

/** Settlement state of a logged value pick, derived by joining it against recorded results. */
enum class ValuePickStatus { PENDING, WON, LOST }

/**
 * A value pick: a fixture where the model's probability for its predicted outcome beat the market's
 * implied probability by at least the edge threshold, logged with the price at flag time so its
 * return can be settled once the result lands.
 */
data class ValuePick(
    val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val round: Int,
    val pickedOutcome: PredictedOutcome,
    val edge: Int,
    val odds: Double,
    val kickoffEpochMillis: Long,
    val status: ValuePickStatus = ValuePickStatus.PENDING,
    /** Latest observed price (the closing line); equals [odds] until the market moves. */
    val closingOdds: Double = odds,
) {
    /** Profit on a 1-unit stake: (odds − 1) if the pick won, −1 if it lost, 0 while pending. */
    val profit: Double
        get() = when (status) {
            ValuePickStatus.WON -> odds - 1.0
            ValuePickStatus.LOST -> -1.0
            ValuePickStatus.PENDING -> 0.0
        }

    /**
     * Closing-line value: how much better the price we took was than the closing line, in percent.
     * Positive means we beat the close (took higher odds than the market settled at) — the sharpest
     * result-independent signal that a pick had genuine edge.
     */
    val clvPercent: Double
        get() = if (closingOdds <= 0.0) 0.0 else (odds / closingOdds - 1.0) * 100.0
}

/**
 * The value-pick ledger rolled up: every pick (sorted strongest edge first) plus the realized ROI
 * over the settled ones — flat-staking 1 unit on each, ROI = total profit ÷ number of settled picks.
 */
data class ValuePicksReport(
    val picks: List<ValuePick> = emptyList(),
    val roiPercent: Int = 0,
    val settledCount: Int = 0,
    val wonCount: Int = 0,
    val pendingCount: Int = 0,
    /** Cumulative ROI (whole percent) after each settled pick, in kickoff order — the trend curve. */
    val roiTrend: List<Int> = emptyList(),
    /** Average closing-line value across picks, in tenths of a percent (e.g. 18 = +1.8%). */
    val clvTenths: Int = 0,
    /** Bankroll growth (percent above the 1.0 start) after each settled pick, half-Kelly staked. */
    val bankrollTrend: List<Int> = emptyList(),
    /** Final bankroll as a multiple of the starting stake (1.0 = break even). */
    val finalBankroll: Double = 1.0,
) {
    val lostCount: Int get() = settledCount - wonCount
    val totalCount: Int get() = picks.size
    /** Whether a meaningful CLV exists (any observed line movement across the ledger). */
    val hasClv: Boolean get() = picks.any { it.closingOdds != it.odds }
}

/** Roll a set of settled picks up into a report: sort strongest edge first, tally ROI over settled picks. */
fun buildValuePicksReport(picks: List<ValuePick>): ValuePicksReport {
    val sorted = picks.sortedByDescending { it.edge }
    val settled = sorted.filter { it.status != ValuePickStatus.PENDING }
    val roi = if (settled.isEmpty()) 0
    else (settled.sumOf { it.profit } / settled.size * 100).roundToInt()
    // Oldest→newest so both curves read left to right over time.
    val chronological = settled.sortedBy { it.kickoffEpochMillis }
    // Running ROI after each settled pick (flat 1-unit stake).
    var runningProfit = 0.0
    val trend = chronological.mapIndexed { i, pick ->
        runningProfit += pick.profit
        (runningProfit / (i + 1) * 100).roundToInt()
    }
    // Bankroll simulation: half-Kelly staking, compounding from a 1.0 start.
    var bankroll = 1.0
    val bankrollTrend = chronological.map { pick ->
        val b = pick.odds - 1.0
        // Edge-based Kelly: EV per unit ≈ (edge/100)·odds, so f* = EV / b.
        val kelly = if (b <= 0.0) 0.0 else (pick.edge / 100.0) * pick.odds / b
        val stake = bankroll * (kelly * KELLY_FRACTION).coerceIn(0.0, MAX_STAKE_FRACTION)
        bankroll += when (pick.status) {
            ValuePickStatus.WON -> stake * b
            ValuePickStatus.LOST -> -stake
            ValuePickStatus.PENDING -> 0.0
        }
        ((bankroll - 1.0) * 100).roundToInt()
    }
    // Average closing-line value across all logged picks, in tenths of a percent for one-decimal display.
    val clvTenths = if (sorted.isEmpty()) 0
    else (sorted.sumOf { it.clvPercent } / sorted.size * 10).roundToInt()
    return ValuePicksReport(
        picks = sorted,
        roiPercent = roi,
        settledCount = settled.size,
        wonCount = settled.count { it.status == ValuePickStatus.WON },
        pendingCount = sorted.count { it.status == ValuePickStatus.PENDING },
        roiTrend = trend,
        clvTenths = clvTenths,
        bankrollTrend = bankrollTrend,
        finalBankroll = bankroll,
    )
}
