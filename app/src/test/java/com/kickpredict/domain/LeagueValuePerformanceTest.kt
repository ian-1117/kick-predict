package com.kickpredict.domain

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.ValuePickStatus
import com.kickpredict.domain.model.buildLeaguePerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueValuePerformanceTest {

    private fun pick(
        id: String,
        league: LeagueType,
        odds: Double,
        status: ValuePickStatus,
    ) = ValuePick(
        matchId = id,
        league = league,
        homeTeam = "H",
        awayTeam = "A",
        round = 1,
        pickedOutcome = PredictedOutcome.HOME_WIN,
        edge = 5,
        odds = odds,
        kickoffEpochMillis = 0L,
        status = status,
    )

    @Test
    fun `ROI is rolled up per league and settled leagues rank above unsettled ones`() {
        val picks = listOf(
            // EPL: one won @2.0 (+1.0) and one lost (-1.0) -> ROI 0%.
            pick("e1", LeagueType.EPL, 2.0, ValuePickStatus.WON),
            pick("e2", LeagueType.EPL, 2.0, ValuePickStatus.LOST),
            // LaLiga: one won @3.0 (+2.0) -> ROI +200%.
            pick("s1", LeagueType.LALIGA, 3.0, ValuePickStatus.WON),
            // Serie A: only pending -> no settled picks, sinks to the bottom.
            pick("i1", LeagueType.SERIE_A, 2.5, ValuePickStatus.PENDING),
        )

        val perf = buildLeaguePerformance(picks)
        assertEquals(3, perf.size)
        // LaLiga (settled, +200%) ranks first; Serie A (unsettled) ranks last.
        assertEquals(LeagueType.LALIGA, perf.first().league)
        assertEquals(LeagueType.SERIE_A, perf.last().league)

        val laliga = perf.first { it.league == LeagueType.LALIGA }
        assertEquals(200, laliga.roiPercent)
        assertEquals(1, laliga.settledCount)

        val epl = perf.first { it.league == LeagueType.EPL }
        assertEquals(0, epl.roiPercent)
        assertEquals(2, epl.betCount)
        assertEquals(2, epl.settledCount)

        val serieA = perf.first { it.league == LeagueType.SERIE_A }
        assertEquals(0, serieA.settledCount)
        assertTrue("unsettled league still counts the logged bet", serieA.betCount == 1)
    }
}
