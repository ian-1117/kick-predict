package com.kickpredict.domain

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.ValuePickStatus
import com.kickpredict.domain.model.buildValuePicksReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePicksReportTest {

    private fun pick(
        id: String,
        odds: Double,
        status: ValuePickStatus,
        kickoff: Long,
        closing: Double = odds,
        edge: Int = 5,
    ) = ValuePick(
        matchId = id,
        league = LeagueType.K_LEAGUE,
        homeTeam = "H",
        awayTeam = "A",
        round = 1,
        pickedOutcome = PredictedOutcome.HOME_WIN,
        edge = edge,
        odds = odds,
        kickoffEpochMillis = kickoff,
        status = status,
        closingOdds = closing,
    )

    @Test
    fun `roi is average profit over settled picks`() {
        val report = buildValuePicksReport(
            listOf(
                pick("a", 2.5, ValuePickStatus.WON, 1),      // +1.5
                pick("b", 2.0, ValuePickStatus.LOST, 2),     // -1.0
                pick("c", 3.0, ValuePickStatus.PENDING, 3),  // excluded from ROI
            ),
        )
        // settled = 2; profit = 1.5 - 1.0 = 0.5; ROI = 0.5 / 2 * 100 = 25
        assertEquals(25, report.roiPercent)
        assertEquals(2, report.settledCount)
        assertEquals(1, report.wonCount)
        assertEquals(1, report.lostCount)
        assertEquals(1, report.pendingCount)
    }

    @Test
    fun `roi trend accumulates in kickoff order`() {
        val report = buildValuePicksReport(
            listOf(
                pick("late", 2.0, ValuePickStatus.LOST, 200),  // 2nd chronologically
                pick("early", 2.0, ValuePickStatus.WON, 100),  // 1st chronologically, +1.0
            ),
        )
        // early: cum +1.0 over 1 -> 100%; then late: cum 0.0 over 2 -> 0%
        assertEquals(listOf(100, 0), report.roiTrend)
    }

    @Test
    fun `clv averages taken-over-closing and flags line movement`() {
        val report = buildValuePicksReport(
            listOf(
                pick("a", 2.10, ValuePickStatus.PENDING, 1, closing = 2.00), // +5.0%
                pick("b", 2.00, ValuePickStatus.PENDING, 2, closing = 2.00), //  0.0%
            ),
        )
        // avg CLV = (5.0 + 0.0) / 2 = 2.5% -> 25 tenths
        assertEquals(25, report.clvTenths)
        assertTrue(report.hasClv)
    }

    @Test
    fun `no clv when the closing line never moved`() {
        val report = buildValuePicksReport(
            listOf(pick("a", 2.10, ValuePickStatus.PENDING, 1)),
        )
        assertEquals(0, report.clvTenths)
        assertFalse(report.hasClv)
    }
}
