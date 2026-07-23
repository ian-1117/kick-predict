package com.kickpredict.domain

import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchNotification
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.usecase.GetPendingNotificationsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationEdgeTest {

    // A current fixture for "m1", kicking off at a fixed instant, so result-recency can be exercised.
    private val kickoff = LocalDateTime.of(2026, 7, 14, 12, 0)
    private val kickoffMillis = kickoff.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun team(id: String) = TeamProfile(
        id = id, name = id, shortName = id, leaguePosition = 5,
        recentForm = listOf(MatchOutcome.WIN), overallRating = 70.0,
        goalsScoredAvg = 1.3, goalsConcededAvg = 1.1, daysSinceLastMatch = 7,
    )
    private val match = Match(
        id = "m1", league = LeagueType.K_LEAGUE,
        homeTeam = team("h"), awayTeam = team("a"),
        kickoff = kickoff, venue = "Stadium",
        headToHead = HeadToHead(emptyList()), context = MatchContext(),
    )

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

    private val awayWinResult = RecordedResult(
        matchId = "m1",
        league = LeagueType.K_LEAGUE,
        homeTeamId = "h",
        awayTeamId = "a",
        homeTeam = "Incheon",
        awayTeam = "Anyang",
        predictedOutcome = PredictedOutcome.AWAY_WIN,
        confidence = 60,
        homeGoals = 0,
        awayGoals = 1,
        recordedAt = 0,
        round = 17,
    )

    // An hour after kickoff — inside the result-recency window.
    private val recentNow = kickoffMillis + 60L * 60_000L

    @Test
    fun `result notification carries the value edge from the ledger`() = runBlocking {
        val useCase = GetPendingNotificationsUseCase(
            liveScores = { emptyMap() },
            odds = { emptyMap() },
            calibrationRepository = FakeRepo(listOf(awayWinResult)),
            valueEdges = { mapOf("m1" to 14) },
        )
        val result = useCase(listOf(match), recentNow).filterIsInstance<MatchNotification.Result>().single()
        assertEquals(14, result.edge)
        assertTrue(result.hit) // predicted away win, final 0–1 → hit
    }

    @Test
    fun `result notification has no edge when the match was not a value pick`() = runBlocking {
        val useCase = GetPendingNotificationsUseCase(
            liveScores = { emptyMap() },
            odds = { emptyMap() },
            calibrationRepository = FakeRepo(listOf(awayWinResult)),
            valueEdges = { emptyMap() },
        )
        val result = useCase(listOf(match), recentNow).filterIsInstance<MatchNotification.Result>().single()
        assertNull(result.edge)
    }

    @Test
    fun `a match that finished long ago does not fire a result notification`() = runBlocking {
        val useCase = GetPendingNotificationsUseCase(
            liveScores = { emptyMap() },
            odds = { emptyMap() },
            calibrationRepository = FakeRepo(listOf(awayWinResult)),
        )
        // A week after kickoff — well past the recency window.
        val staleNow = kickoffMillis + 7L * 24 * 60 * 60_000L
        val results = useCase(listOf(match), staleNow).filterIsInstance<MatchNotification.Result>()
        assertTrue("stale results should be suppressed", results.isEmpty())
    }
}
