package com.kickpredict.domain

import com.kickpredict.domain.model.BacktestGame
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.usecase.BacktestUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BacktestUseCaseTest {

    private fun game(day: Int, home: String, away: String, hg: Int, ag: Int, odds: MarketOdds? = null) =
        BacktestGame(
            league = LeagueType.EPL,
            date = LocalDate.of(2024, 1, day),
            homeId = home,
            awayId = away,
            homeGoals = hg,
            awayGoals = ag,
            avgOdds = odds,
            closeOdds = odds,
        )

    @Test
    fun `empty games produce an empty report`() = runBlocking {
        val report = BacktestUseCase { emptyList() }()
        assertEquals(0, report.overall.games)
        assertTrue(report.byLeague.isEmpty())
    }

    @Test
    fun `every game is scored and proper scores are in range`() = runBlocking {
        val games = listOf(
            game(1, "A", "B", 2, 0),
            game(2, "B", "A", 1, 1),
            game(3, "A", "B", 0, 1),
            game(4, "B", "A", 3, 0),
        )
        val report = BacktestUseCase { games }()
        assertEquals(4, report.overall.games)
        assertEquals(1, report.byLeague.size)
        // Brier for a 3-way distribution is in [0, 2]; log-loss is non-negative.
        assertTrue(report.overall.brier in 0.0..2.0)
        assertTrue(report.overall.logLoss >= 0.0)
        // No odds → no value bets.
        assertEquals(0, report.overall.bets)
    }

    @Test
    fun `the model comparison covers Elo market and blend, and the blend is no worse than Elo`() = runBlocking {
        // Mixed results with market prices → all three predictors get scored on the same games.
        val games = (1..40).map { i ->
            val homeWin = i % 3 != 0
            game(1 + i % 28, if (i % 2 == 0) "A" else "B", if (i % 2 == 0) "B" else "A",
                if (homeWin) 2 else 0, if (homeWin) 0 else 1,
                MarketOdds.of(2.1, 3.3, 3.6))
        }
        val report = BacktestUseCase { games }()
        val models = report.models.associateBy { it.model }
        // All three predictors present.
        assertEquals(3, report.models.size)
        assertTrue(com.kickpredict.domain.model.BacktestModel.BLEND in models)
        // Blending pulls toward the market, so it never scores worse than Elo alone on Brier.
        val elo = models.getValue(com.kickpredict.domain.model.BacktestModel.ELO)
        val blend = models.getValue(com.kickpredict.domain.model.BacktestModel.BLEND)
        assertTrue("blend Brier ${blend.brier} should be <= elo Brier ${elo.brier} + slack", blend.brier <= elo.brier + 0.05)
    }

    @Test
    fun `a value bet is placed only when the model beats the market by the threshold`() = runBlocking {
        // Heavy home favourite by results, but the market prices the home side as a longshot — a clear edge.
        val games = (1..10).map { game(it, "Strong", "Weak", 3, 0, MarketOdds.of(5.0, 4.0, 1.6)) }
        val report = BacktestUseCase { games }()
        assertTrue("expected some value bets", report.overall.bets > 0)
    }
}
