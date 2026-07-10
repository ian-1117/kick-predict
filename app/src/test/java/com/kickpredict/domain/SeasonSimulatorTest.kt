package com.kickpredict.domain

import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.domain.calibration.CalibrationDefaults
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.simulation.ScoreGrid
import com.kickpredict.domain.simulation.SeasonSimulator
import com.kickpredict.domain.standings.TableEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SeasonSimulatorTest {

    private val engine = PredictionEngine()
    private val simulator = SeasonSimulator(CalibrationDefaults)

    private fun season(league: LeagueType = LeagueType.K_LEAGUE, rounds: Int = 10): List<Match> =
        MockDataProvider.leagueSchedule(league, rounds).map { it.copy(predictedResult = engine.predict(it)) }

    @Test
    fun `title probabilities across the league sum to one`() {
        val projection = simulator.simulate(
            league = LeagueType.K_LEAGUE,
            played = emptyList(),
            remaining = season(),
            iterations = 2_000,
        )
        val total = projection.teams.sumOf { it.titleProbability }
        assertEquals(1.0, total, 1e-9)
    }

    @Test
    fun `every team is projected into exactly one place per iteration`() {
        val projection = simulator.simulate(
            league = LeagueType.EPL,
            played = emptyList(),
            remaining = season(LeagueType.EPL),
            iterations = 1_000,
        )
        val teamCount = projection.teams.size
        // Mean of the finishing positions 1..n is (n+1)/2, whatever the ordering.
        val meanRank = projection.teams.sumOf { it.averageRank } / teamCount
        assertEquals((teamCount + 1) / 2.0, meanRank, 1e-9)
        assertEquals(1.0, projection.teams.sumOf { it.continentalProbability } / projection.rules.continentalSpots, 1e-9)
    }

    @Test
    fun `a completed season leaves the real leader as champion with certainty`() {
        val fixtures = season(rounds = 4)
        // Award every match to the home side of the first fixture's home team, so it must top the table.
        val champion = fixtures.first().homeTeam.id
        val played = fixtures.map { match ->
            val championIsHome = match.homeTeam.id == champion
            val championIsAway = match.awayTeam.id == champion
            val (homeGoals, awayGoals) = when {
                championIsHome -> 3 to 0
                championIsAway -> 0 to 3
                else -> 0 to 0
            }
            TableEntry(match.homeTeam.id, match.homeTeam.name, match.awayTeam.id, match.awayTeam.name, homeGoals, awayGoals)
        }

        val projection = simulator.simulate(
            league = LeagueType.K_LEAGUE,
            played = played,
            remaining = emptyList(),
            iterations = 50,
        )

        assertTrue(projection.isComplete)
        val leader = projection.teams.first()
        assertEquals(champion, leader.teamId)
        assertEquals(1.0, leader.titleProbability, 1e-9)
    }

    @Test
    fun `a points lead carried into the cutoff raises the title probability`() {
        val fixtures = season(rounds = 10)
        // The weakest side, so its cold title probability has room to climb.
        val underdog = fixtures.minByOrNull { it.homeTeam.overallRating }!!.homeTeam.id

        val cold = simulator.simulate(LeagueType.K_LEAGUE, emptyList(), fixtures, iterations = 3_000)
        val coldProbability = cold.teams.first { it.teamId == underdog }.titleProbability

        // Same remaining fixtures, but the underdog banks 30 points before the cutoff.
        val opponents = fixtures.map { it.awayTeam }.distinctBy { it.id }.filter { it.id != underdog }.take(10)
        val headStart = opponents.map { opponent ->
            TableEntry(underdog, "Underdog", opponent.id, opponent.name, 3, 0)
        }
        val hot = simulator.simulate(LeagueType.K_LEAGUE, headStart, fixtures, iterations = 3_000)
        val hotProbability = hot.teams.first { it.teamId == underdog }.titleProbability

        assertTrue(
            "a 30-point head start should raise the title probability (cold=$coldProbability hot=$hotProbability)",
            hotProbability > coldProbability,
        )
    }

    @Test
    fun `a fixture with no prediction is rejected rather than quietly dropped`() {
        val fixtures = MockDataProvider.leagueSchedule(LeagueType.K_LEAGUE, rounds = 1)
        val failure = runCatching {
            simulator.simulate(LeagueType.K_LEAGUE, emptyList(), fixtures, iterations = 10)
        }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `the same seed reproduces the projection`() {
        val fixtures = season(rounds = 6)
        val first = simulator.simulate(LeagueType.K_LEAGUE, emptyList(), fixtures, iterations = 500, seed = 42L)
        val second = simulator.simulate(LeagueType.K_LEAGUE, emptyList(), fixtures, iterations = 500, seed = 42L)
        assertEquals(first.teams.map { it.teamId to it.titleProbability }, second.teams.map { it.teamId to it.titleProbability })
    }

    @Test
    fun `sampling a score grid reproduces the probabilities the engine reports`() {
        val match = season(rounds = 1).first()
        val prediction = match.predictedResult!!
        val calibration = CalibrationDefaults.forLeague(match.league)
        val grid = ScoreGrid(prediction.expectedHomeGoals, prediction.expectedAwayGoals, calibration.drawInflation)

        // The grid's closed-form probabilities are what the engine published.
        assertEquals(prediction.homeWinPercent / 100.0, grid.homeWinProbability, 0.01)
        assertEquals(prediction.drawPercent / 100.0, grid.drawProbability, 0.01)
        assertEquals(prediction.awayWinPercent / 100.0, grid.awayWinProbability, 0.01)

        // And drawing from it converges to the same numbers.
        val random = Random(7)
        val samples = 40_000
        var homeWins = 0
        var draws = 0
        repeat(samples) {
            val packed = grid.sample(random)
            val home = ScoreGrid.unpackHome(packed)
            val away = ScoreGrid.unpackAway(packed)
            when {
                home > away -> homeWins++
                home == away -> draws++
            }
        }
        assertEquals(grid.homeWinProbability, homeWins.toDouble() / samples, 0.02)
        assertEquals(grid.drawProbability, draws.toDouble() / samples, 0.02)
    }
}
