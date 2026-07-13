package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.BacktestGame
import com.kickpredict.domain.model.BacktestLeagueResult
import com.kickpredict.domain.model.BacktestReport
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.rating.EloModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Honest walk-forward backtest over the bundled historical seasons: for each match in date order the
 * learned Elo model predicts from **only what came before**, is scored, and only then trained on the
 * result — so nothing peeks at its own future. Reports accuracy + proper scores (Brier / log-loss)
 * over every league, and flat-stake value-betting ROI + closing-line value over the leagues that ship
 * odds. A value bet is placed when the model's edge over the average market price clears [EDGE_THRESHOLD].
 */
class BacktestUseCase(
    private val games: () -> List<BacktestGame>,
) {

    suspend operator fun invoke(): BacktestReport = withContext(Dispatchers.Default) {
        val accByLeague = games()
            .groupBy { it.league }
            .mapValues { (_, gs) -> scoreLeague(gs) }
            .filterValues { it.games > 0 }
        val byLeague = accByLeague
            .map { (league, acc) -> acc.toResult(league) }
            .sortedByDescending { it.games }
        val overall = accByLeague.values.fold(Acc()) { a, b -> a + b }.toResult(null)
        BacktestReport(overall = overall, byLeague = byLeague)
    }

    private fun scoreLeague(games: List<BacktestGame>): Acc {
        val elo = EloModel()
        var acc = Acc()
        games.sortedBy { it.date }.forEach { g ->
            val (pH, pD, pA) = elo.predict(g.homeId, g.awayId)
            val raw = doubleArrayOf(pH, pD, pA)
            val sum = raw.sum().takeIf { it > 0.0 } ?: 1.0
            val p = DoubleArray(3) { raw[it] / sum }
            val actualIdx = g.actual.index()
            val predIdx = (0..2).maxBy { p[it] }

            var brier = 0.0
            for (k in 0..2) {
                val y = if (k == actualIdx) 1.0 else 0.0
                brier += (p[k] - y).pow(2)
            }
            val logLoss = -ln(p[actualIdx].coerceIn(1e-4, 1.0))

            // Value bet: back the outcome with the biggest edge over the average market price.
            var betProfit = 0.0
            var betWon = 0
            var placed = 0
            var clv = 0.0
            var clvCounted = 0
            val odds = g.avgOdds
            if (odds != null) {
                var bestIdx = -1
                var bestEdge = 0.0
                for (idx in 0..2) {
                    val edge = p[idx] - odds.percentFor(outcomeOf(idx)) / 100.0
                    if (edge > bestEdge) { bestEdge = edge; bestIdx = idx }
                }
                if (bestIdx >= 0 && bestEdge * 100 >= EDGE_THRESHOLD) {
                    placed = 1
                    val outcome = outcomeOf(bestIdx)
                    val price = odds.oddsFor(outcome)
                    if (bestIdx == actualIdx) { betProfit = price - 1.0; betWon = 1 } else betProfit = -1.0
                    g.closeOdds?.let { close ->
                        clv = price / close.oddsFor(outcome) - 1.0
                        clvCounted = 1
                    }
                }
            }

            acc = acc + Acc(
                games = 1,
                correct = if (predIdx == actualIdx) 1 else 0,
                brierSum = brier,
                logLossSum = logLoss,
                bets = placed,
                betsWon = betWon,
                profit = betProfit,
                clvSum = clv,
                clvCount = clvCounted,
            )
            elo.update(g.homeId, g.awayId, g.homeGoals, g.awayGoals)
        }
        return acc
    }

    private data class Acc(
        val games: Int = 0,
        val correct: Int = 0,
        val brierSum: Double = 0.0,
        val logLossSum: Double = 0.0,
        val bets: Int = 0,
        val betsWon: Int = 0,
        val profit: Double = 0.0,
        val clvSum: Double = 0.0,
        val clvCount: Int = 0,
    ) {
        operator fun plus(o: Acc) = Acc(
            games + o.games, correct + o.correct, brierSum + o.brierSum, logLossSum + o.logLossSum,
            bets + o.bets, betsWon + o.betsWon, profit + o.profit, clvSum + o.clvSum, clvCount + o.clvCount,
        )

        fun toResult(league: LeagueType?) = BacktestLeagueResult(
            league = league,
            games = games,
            correct = correct,
            brier = if (games == 0) 0.0 else brierSum / games,
            logLoss = if (games == 0) 0.0 else logLossSum / games,
            bets = bets,
            betsWon = betsWon,
            roiPercent = if (bets == 0) 0 else (profit / bets * 100).roundToInt(),
            clvTenths = if (clvCount == 0) 0 else (clvSum / clvCount * 1000).roundToInt(),
        )
    }

    private fun outcomeOf(idx: Int): PredictedOutcome = when (idx) {
        0 -> PredictedOutcome.HOME_WIN
        1 -> PredictedOutcome.DRAW
        else -> PredictedOutcome.AWAY_WIN
    }

    private fun PredictedOutcome.index(): Int = when (this) {
        PredictedOutcome.HOME_WIN -> 0
        PredictedOutcome.DRAW -> 1
        PredictedOutcome.AWAY_WIN -> 2
    }

    companion object {
        /** Model must beat the average market price by this many points for a value bet. */
        const val EDGE_THRESHOLD = 5
    }
}
