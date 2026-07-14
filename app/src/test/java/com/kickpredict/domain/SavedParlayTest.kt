package com.kickpredict.domain

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.ParlayLeg
import com.kickpredict.domain.model.ParlayStatus
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.SavedParlay
import com.kickpredict.domain.model.buildParlaysReport
import com.kickpredict.domain.model.settleParlay
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedParlayTest {

    private fun leg(id: String, pick: PredictedOutcome, odds: Double) =
        ParlayLeg(id, LeagueType.EPL, "H", "A", pick, odds)

    private fun parlay(id: String, createdAt: Long, vararg legs: ParlayLeg) = SavedParlay(
        id = id,
        legs = legs.toList(),
        comboOdds = legs.fold(1.0) { acc, l -> acc * l.odds },
        edgePercent = 5,
        kellyStakeFraction = 0.02,
        createdAt = createdAt,
    )

    private val H = PredictedOutcome.HOME_WIN
    private val A = PredictedOutcome.AWAY_WIN

    @Test
    fun `parlay wins only when every leg wins`() {
        val p = parlay("p", 1L, leg("a", H, 2.0), leg("b", H, 3.0))
        val settled = settleParlay(p, mapOf("a" to H, "b" to H))
        assertEquals(ParlayStatus.WON, settled.status)
        assertEquals(5.0, settled.profit, 1e-9) // combo 6.0 -> profit 5.0 on a 1-unit stake
    }

    @Test
    fun `a single losing leg sinks the parlay even if others are unsettled`() {
        val p = parlay("p", 1L, leg("a", H, 2.0), leg("b", H, 3.0))
        val settled = settleParlay(p, mapOf("a" to A)) // a lost, b has no result yet
        assertEquals(ParlayStatus.LOST, settled.status)
        assertEquals(-1.0, settled.profit, 1e-9)
    }

    @Test
    fun `parlay is pending while any leg is unsettled and none lost`() {
        val p = parlay("p", 1L, leg("a", H, 2.0), leg("b", H, 3.0))
        val settled = settleParlay(p, mapOf("a" to H)) // a won, b pending
        assertEquals(ParlayStatus.PENDING, settled.status)
        assertEquals(0.0, settled.profit, 1e-9)
    }

    @Test
    fun `report rolls up ROI over settled parlays newest first`() {
        val won = parlay("won", 2L, leg("a", H, 2.0), leg("b", H, 2.0)) // combo 4.0 -> +3
        val lost = parlay("lost", 1L, leg("c", H, 2.0), leg("d", H, 2.0)) // -1
        val actuals = mapOf("a" to H, "b" to H, "c" to A, "d" to H)
        val report = buildParlaysReport(listOf(lost, won), actuals)
        assertEquals("won", report.parlays.first().parlay.id) // newest (createdAt 2) first
        assertEquals(2, report.settledCount)
        assertEquals(1, report.wonCount)
        // (3 + -1) / 2 * 100 = 100
        assertEquals(100, report.roiPercent)
    }
}
