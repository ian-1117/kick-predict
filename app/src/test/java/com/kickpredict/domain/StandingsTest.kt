package com.kickpredict.domain

import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.usecase.GetStandingsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StandingsTest {

    private class FakeRepo(private val results: List<RecordedResult>) : CalibrationRepository {
        override suspend fun recordPredictions(matches: List<Match>) {}
        override suspend fun recordResult(matchId: String, homeGoals: Int, awayGoals: Int) {}
        override suspend fun replaceResults(results: Map<String, Pair<Int, Int>>) {}
        override suspend fun getResult(matchId: String): ActualResult? = null
        override suspend fun recordedResultCount() = results.size
        override suspend fun recordedResults() = results
        override suspend fun predictionRecords(): List<PredictionRecord> = emptyList()
        override suspend fun history(): List<HistoricalMatch> = emptyList()
    }

    private fun result(id: String, homeId: String, awayId: String, hg: Int, ag: Int) =
        RecordedResult(
            matchId = id, league = LeagueType.EPL,
            homeTeamId = homeId, awayTeamId = awayId, homeTeam = homeId, awayTeam = awayId,
            predictedOutcome = PredictedOutcome.HOME_WIN, confidence = 50,
            homeGoals = hg, awayGoals = ag, recordedAt = 0L,
        )

    @Test
    fun `standings tally points, goal difference and ranking`() = runBlocking {
        // A beats B 2-0, A beats C 1-1 (draw), B beats C 3-0.
        val repo = FakeRepo(
            listOf(
                result("m1", "A", "B", 2, 0),
                result("m2", "A", "C", 1, 1),
                result("m3", "B", "C", 3, 0),
            ),
        )
        val table = GetStandingsUseCase(repo)().getValue(LeagueType.EPL)

        assertEquals(3, table.size)
        // A: W1 D1 -> 4 pts; B: W1 L1 -> 3 pts; C: D1 L1 -> 1 pt.
        assertEquals("A", table[0].teamId)
        assertEquals(4, table[0].points)
        assertEquals("B", table[1].teamId)
        assertEquals(3, table[1].points)
        assertEquals("C", table[2].teamId)
        assertEquals(1, table[2].points)
        assertEquals(2, table[0].goalDiff) // A: 3 for, 1 against -> +2
    }
}
