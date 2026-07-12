package com.kickpredict.domain.model

import kotlin.math.roundToInt

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
    // Running ROI after each settled pick, oldest→newest, so the chart reads left to right over time.
    var runningProfit = 0.0
    val trend = settled.sortedBy { it.kickoffEpochMillis }.mapIndexed { i, pick ->
        runningProfit += pick.profit
        (runningProfit / (i + 1) * 100).roundToInt()
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
    )
}
