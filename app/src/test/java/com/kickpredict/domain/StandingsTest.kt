package com.kickpredict.domain

import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.usecase.GetStandingsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

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

    /** Returns a fixture per id so the use case counts those results as current-season. */
    private class FakeMatchRepo(private val ids: List<String>) : MatchRepository {
        private fun team(id: String) = TeamProfile(
            id = id, name = id, shortName = id, leaguePosition = 1,
            recentForm = listOf(MatchOutcome.WIN), overallRating = 70.0,
            goalsScoredAvg = 1.3, goalsConcededAvg = 1.1, daysSinceLastMatch = 7,
        )
        private fun match(id: String) = Match(
            id = id, league = LeagueType.EPL, homeTeam = team("H$id"), awayTeam = team("A$id"),
            kickoff = LocalDateTime.of(2026, 7, 20, 20, 0), venue = "V",
            headToHead = HeadToHead(emptyList()), context = MatchContext(),
        )
        override suspend fun getMatches(forceRefresh: Boolean): List<Match> = ids.map { match(it) }
        override suspend fun cachedMatches(): List<Match> = ids.map { match(it) }
        override suspend fun getMatch(id: String): Match? = null
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
        val matchRepo = FakeMatchRepo(listOf("m1", "m2", "m3"))
        val table = GetStandingsUseCase(matchRepo, repo)().getValue(LeagueType.EPL)

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

    @Test
    fun `results outside the current fixture set are excluded`() = runBlocking {
        val repo = FakeRepo(
            listOf(
                result("m1", "A", "B", 2, 0),
                result("stale", "A", "B", 0, 9), // a leftover result not in this season's fixtures
            ),
        )
        // Only m1 is a current fixture; the stale result must not affect the table.
        val table = GetStandingsUseCase(FakeMatchRepo(listOf("m1")), repo)().getValue(LeagueType.EPL)
        assertEquals(1, table.first { it.teamId == "A" }.played)
        assertEquals(3, table.first { it.teamId == "A" }.points) // just the 2-0 win
    }
}
