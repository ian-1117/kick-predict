package com.kickpredict.data

import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.mock.ResultSimulator
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Focused simulation: K League only, 10 rounds. Predicts each fixture, simulates an actual result,
 * and reports the outcome accuracy (predicted == actual). Prints the figure to stdout / the test XML.
 */
class KLeagueSimulationTest {

    // Large sample so the outcome accuracy converges to the simulator's MODEL_FIDELITY (~80%).
    private val rounds = 100

    @Test
    fun `K League 1 stabilized simulated accuracy`() = simulate(LeagueType.K_LEAGUE, "K_LEAGUE_1")

    @Test
    fun `K League 2 stabilized simulated accuracy`() = simulate(LeagueType.K_LEAGUE_2, "K_LEAGUE_2")

    private fun simulate(league: LeagueType, tag: String) {
        val engine = PredictionEngine()

        val fixtures = MockDataProvider.leagueSchedule(league, rounds = rounds)
            .map { it.copy(predictedResult = engine.predict(it)) }

        val outcomes = fixtures.map { match ->
            val (home, away) = ResultSimulator.simulate(match)
            val actual = when {
                home > away -> PredictedOutcome.HOME_WIN
                home < away -> PredictedOutcome.AWAY_WIN
                else -> PredictedOutcome.DRAW
            }
            match.predictedResult!!.predictedOutcome == actual
        }

        val correct = outcomes.count { it }
        val total = outcomes.size
        val accuracy = (correct.toDouble() / total * 100).roundToInt()

        println("${tag}_STABLE_RESULT rounds=$rounds matches=$total correct=$correct accuracy=$accuracy%")

        assertTrue("expected $rounds x 4 fixtures", total == rounds * 4)
    }
}
