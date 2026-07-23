package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.BacktestGame
import com.kickpredict.domain.model.BacktestLeagueResult
import com.kickpredict.domain.model.BacktestModel
import com.kickpredict.domain.model.BacktestModelResult
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
        val scoreByLeague = games()
            .groupBy { it.league }
            .mapValues { (_, gs) -> scoreLeague(gs) }
            .filterValues { it.elo.games > 0 }
        val byLeague = scoreByLeague
            .map { (league, s) -> s.elo.toResult(league) }
            .sortedByDescending { it.games }
        val overall = scoreByLeague.values.fold(Acc()) { a, b -> a + b.elo }.toResult(null)
        // Predictor comparison over the odds-bearing games (Elo vs market vs blend).
        val models = BacktestModel.entries.map { model ->
            scoreByLeague.values.fold(Acc()) { a, b -> a + (b.models[model] ?: Acc()) }.toModelResult(model)
        }.filter { it.games > 0 }
        BacktestReport(overall = overall, byLeague = byLeague, models = models)
    }

    private class LeagueScore(val elo: Acc, val models: Map<BacktestModel, Acc>)

    private fun scoreLeague(games: List<BacktestGame>): LeagueScore {
        val elo = EloModel()
        var eloAll = Acc()
        val modelAcc = mutableMapOf(BacktestModel.ELO to Acc(), BacktestModel.MARKET to Acc(), BacktestModel.BLEND to Acc())
        games.sortedBy { it.date }.forEach { g ->
            val (pH, pD, pA) = elo.predict(g.homeId, g.awayId)
            val eloProbs = normalise(doubleArrayOf(pH, pD, pA))
            // Elo over every game (this feeds the per-league / overall Elo view).
            eloAll += scoreModel(eloProbs, g)

            val odds = g.avgOdds
            if (odds != null) {
                val marketProbs = normalise(doubleArrayOf(odds.homePercent / 100.0, odds.drawPercent / 100.0, odds.awayPercent / 100.0))
                val blendProbs = normalise(DoubleArray(3) { BLEND_WEIGHT * eloProbs[it] + (1 - BLEND_WEIGHT) * marketProbs[it] })
                modelAcc[BacktestModel.ELO] = modelAcc.getValue(BacktestModel.ELO) + scoreModel(eloProbs, g)
                modelAcc[BacktestModel.MARKET] = modelAcc.getValue(BacktestModel.MARKET) + scoreModel(marketProbs, g)
                modelAcc[BacktestModel.BLEND] = modelAcc.getValue(BacktestModel.BLEND) + scoreModel(blendProbs, g)
            }
            elo.update(g.homeId, g.awayId, g.homeGoals, g.awayGoals)
        }
        return LeagueScore(eloAll, modelAcc)
    }

    /** Score one predictor's probabilities on one game: accuracy, proper scores, and a value bet vs the market. */
    private fun scoreModel(p: DoubleArray, g: BacktestGame): Acc {
        val actualIdx = g.actual.index()
        val predIdx = (0..2).maxBy { p[it] }
        var brier = 0.0
        for (k in 0..2) {
            val y = if (k == actualIdx) 1.0 else 0.0
            brier += (p[k] - y).pow(2)
        }
        val logLoss = -ln(p[actualIdx].coerceIn(1e-4, 1.0))

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
        return Acc(
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
    }

    private fun normalise(raw: DoubleArray): DoubleArray {
        val sum = raw.sum().takeIf { it > 0.0 } ?: 1.0
        return DoubleArray(3) { raw[it] / sum }
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

        fun toModelResult(model: BacktestModel) = BacktestModelResult(
            model = model,
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

        /** Blend weight on the model vs the market: 0.5 = an even mix of Elo and the market price. */
        const val BLEND_WEIGHT = 0.5
    }
}
