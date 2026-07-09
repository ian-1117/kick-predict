package com.kickpredict.domain

import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.domain.calibration.Calibrator
import com.kickpredict.domain.calibration.ConfidenceCalibrator
import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.TeamAvailability
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Pure-JVM tests for [PredictionEngine], driven by the three product scenarios in
 * [MockDataProvider]. These run under `./gradlew :app:testDebugUnitTest`.
 */
class PredictionEngineTest {

    private val engine = PredictionEngine()
    private val matches = MockDataProvider.matches().associateBy { it.id }

    private fun predict(id: String) = engine.predict(matches.getValue(id))

    @Test
    fun `probabilities always sum to 100`() {
        matches.keys.forEach { id ->
            val r = predict(id)
            assertEquals(
                "sum for $id",
                100,
                r.homeWinPercent + r.drawPercent + r.awayWinPercent,
            )
        }
    }

    @Test
    fun `over-under and BTTS markets are well-formed`() {
        matches.keys.forEach { id ->
            val r = predict(id)
            assertTrue("over in range for $id", r.overProbabilityPercent in 0..100)
            assertTrue("btts in range for $id", r.bttsProbabilityPercent in 0..100)
            assertEquals("over+under=100 for $id", 100, r.overProbabilityPercent + r.underProbabilityPercent)
        }
    }

    @Test
    fun `top scorelines are ranked and well-formed`() {
        matches.keys.forEach { id ->
            val r = predict(id)
            assertTrue("scorelines present for $id", r.topScorelines.isNotEmpty())
            val probs = r.topScorelines.map { it.probabilityPercent }
            assertEquals("sorted desc for $id", probs.sortedDescending(), probs)
            r.topScorelines.forEach { assertTrue("prob in range for $id", it.probabilityPercent in 1..100) }
        }
    }

    @Test
    fun `scenario A - top vs bottom is a high-confidence home win`() {
        val r = predict("match_a")
        assertEquals(PredictedOutcome.HOME_WIN, r.predictedOutcome)
        assertTrue("confidence should be very high but was ${r.confidenceScore}", r.confidenceScore >= 80)
        assertEquals(ConfidenceTier.VERY_HIGH, r.confidenceTier)
    }

    @Test
    fun `scenario B - two draw-prone sides favour a draw with moderate confidence`() {
        val r = predict("match_b")
        assertEquals(PredictedOutcome.DRAW, r.predictedOutcome)
        assertTrue("confidence should be moderate but was ${r.confidenceScore}", r.confidenceScore in 45..64)
    }

    @Test
    fun `scenario C - fatigue variable produces a low-confidence prediction`() {
        val r = predict("match_c")
        assertTrue("confidence should be low but was ${r.confidenceScore}", r.confidenceScore < 50)
    }

    @Test
    fun `confidence ordering is A greater than B greater than C`() {
        val a = predict("match_a").confidenceScore
        val b = predict("match_b").confidenceScore
        val c = predict("match_c").confidenceScore
        assertTrue("expected $a > $b > $c", a > b && b > c)
    }

    @Test
    fun `scenario D - a bogey team's 상성 pulls a paper mismatch level`() {
        val r = predict("match_d")
        // Home is 14th & weaker on paper, but owns the H2H -> matchup favours home and lifts it to
        // at least level with the title-contending visitor.
        assertEquals(com.kickpredict.domain.model.MatchupEdge.HOME, r.matchupEdge)
        assertTrue("home should not trail after 상성 (${r.homeWinPercent} vs ${r.awayWinPercent})",
            r.homeWinPercent >= r.awayWinPercent)
    }

    @Test
    fun `matchup (상성) raises the historically dominant side's win probability`() {
        // Identical teams & fixture; only the head-to-head record changes.
        val balanced = fixture(h2h(A, H, D, H, A))
        val homeBogey = fixture(h2h(H, H, H, D, H))

        val balancedHome = engine.predict(balanced).homeWinPercent
        val dominantHome = engine.predict(homeBogey).homeWinPercent

        assertTrue(
            "H2H dominance should lift home win% ($balancedHome -> $dominantHome)",
            dominantHome > balancedHome,
        )
    }

    @Test
    fun `H2H recency weighting - same record, recent results matter more`() {
        // Same aggregate record (2 home wins, 2 away wins) but opposite recency.
        val recentHomeWins = fixture(h2h(H, H, A, A)) // last two went home's way
        val recentAwayWins = fixture(h2h(A, A, H, H)) // last two went away's way

        val homeWhenTrendingUp = engine.predict(recentHomeWins).homeWinPercent
        val homeWhenTrendingDown = engine.predict(recentAwayWins).homeWinPercent

        assertTrue(
            "recent home wins should out-predict recent away wins for the same tally " +
                "($homeWhenTrendingUp vs $homeWhenTrendingDown)",
            homeWhenTrendingUp > homeWhenTrendingDown,
        )
    }

    private fun team(id: String, rating: Double, position: Int) = TeamProfile(
        id = id,
        name = id,
        shortName = id,
        leaguePosition = position,
        recentForm = listOf(MatchOutcome.WIN, MatchOutcome.DRAW, MatchOutcome.LOSS),
        overallRating = rating,
        goalsScoredAvg = 1.4,
        goalsConcededAvg = 1.2,
        daysSinceLastMatch = 7,
    )

    private fun fixture(h2h: HeadToHead, context: MatchContext = MatchContext()) = Match(
        id = "matchup_test",
        league = LeagueType.EPL,
        homeTeam = team("HME", rating = 74.0, position = 10),
        awayTeam = team("AWY", rating = 76.0, position = 8),
        kickoff = LocalDateTime.of(2026, 7, 20, 20, 0),
        venue = "Test Park",
        headToHead = h2h,
        context = context,
    )

    @Test
    fun `away injuries raise the home win probability`() {
        val fullStrength = fixture(h2h())
        val awayWeakened = fixture(
            h2h(),
            MatchContext(awayAvailability = TeamAvailability(keyPlayersInjured = 2, lineupStrengthPercent = 80)),
        )
        val home = engine.predict(fullStrength).homeWinPercent
        val homeVsWeakened = engine.predict(awayWeakened).homeWinPercent
        assertTrue("away injuries should help home ($home -> $homeVsWeakened)", homeVsWeakened > home)
    }

    @Test
    fun `adverse weather increases the draw probability`() {
        val clear = fixture(h2h(), MatchContext(weather = Weather.CLEAR))
        val snow = fixture(h2h(), MatchContext(weather = Weather.SNOW))
        val drawClear = engine.predict(clear).drawPercent
        val drawSnow = engine.predict(snow).drawPercent
        assertTrue("snow should raise draw% ($drawClear -> $drawSnow)", drawSnow > drawClear)
    }

    @Test
    fun `confidence calibrator pulls down a systematically over-confident model`() {
        // History where the model claims high confidence but is only right ~60% of the time.
        val history = buildList {
            repeat(500) { add(PredictionRecord(confidence = 90, wasCorrect = it % 10 < 6)) }
            repeat(500) { add(PredictionRecord(confidence = 50, wasCorrect = it % 10 < 4)) }
        }
        val calibration = ConfidenceCalibrator.fit(history)
        val calibrated90 = calibration.calibrate(90)
        assertTrue("90% should be pulled down toward ~60 (was $calibrated90)", calibrated90 < 90)
        assertTrue("calibration should stay monotone", calibration.calibrate(90) >= calibration.calibrate(50))
    }

    // H2H outcome aliases (home perspective) for terse, most-recent-first fixtures.
    private val H = H2HOutcome.HOME_WIN
    private val D = H2HOutcome.DRAW
    private val A = H2HOutcome.AWAY_WIN
    private fun h2h(vararg outcomes: H2HOutcome) =
        HeadToHead(outcomes.map { H2HMeeting(it, atHomeVenue = false) })
    private fun h2hV(vararg meetings: Pair<H2HOutcome, Boolean>) =
        HeadToHead(meetings.map { H2HMeeting(it.first, atHomeVenue = it.second) })

    @Test
    fun `home-venue H2H meetings weigh more than away-venue ones`() {
        // Same 2-0-2 tally & order; only which meetings were at this venue differs.
        val homeWinsAtVenue = fixture(h2hV(H to true, H to true, A to false, A to false))
        val homeWinsAwayVenue = fixture(h2hV(H to false, H to false, A to true, A to true))

        val homePctVenueBoosted = engine.predict(homeWinsAtVenue).homeWinPercent
        val homePctVenueMuted = engine.predict(homeWinsAwayVenue).homeWinPercent

        assertTrue(
            "home wins at this venue should count for more ($homePctVenueBoosted vs $homePctVenueMuted)",
            homePctVenueBoosted > homePctVenueMuted,
        )
    }

    @Test
    fun `calibrator recovers league scoring averages from history`() {
        val history = buildList {
            repeat(40) { add(HistoricalMatch(LeagueType.EPL, homeGoals = 2, awayGoals = 1)) }
            repeat(30) { add(HistoricalMatch(LeagueType.EPL, homeGoals = 1, awayGoals = 1)) }
            repeat(30) { add(HistoricalMatch(LeagueType.EPL, homeGoals = 0, awayGoals = 2)) }
        }
        val cal = Calibrator.calibrateLeague(history)
        assertEquals(1.10, cal.homeGoalsAvg, 1e-6)
        assertEquals(1.30, cal.awayGoalsAvg, 1e-6)
        assertTrue("draw inflation in range: ${cal.drawInflation}", cal.drawInflation in 0.8..1.6)
    }
}
