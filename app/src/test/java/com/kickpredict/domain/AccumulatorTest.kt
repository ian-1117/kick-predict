package com.kickpredict.domain

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.buildAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccumulatorTest {

    private fun leg(odds: Double, edge: Int, id: String = "m$odds") = ValuePick(
        matchId = id,
        league = LeagueType.EPL,
        homeTeam = "H",
        awayTeam = "A",
        round = 1,
        pickedOutcome = PredictedOutcome.HOME_WIN,
        edge = edge,
        odds = odds,
        kickoffEpochMillis = 0L,
    )

    @Test
    fun `fewer than two legs is not a parlay`() {
        assertNull(buildAccumulator(emptyList()))
        assertNull(buildAccumulator(listOf(leg(2.0, 5, "a"))))
    }

    @Test
    fun `combo odds are the product of the legs`() {
        val acca = buildAccumulator(listOf(leg(2.0, 5, "a"), leg(1.5, 4, "b"), leg(3.0, 6, "c")))!!
        assertEquals(3, acca.legCount)
        assertEquals(9.0, acca.comboOdds, 1e-9) // 2.0 * 1.5 * 3.0
    }

    @Test
    fun `joint probabilities are the product of the legs and model beats market with positive edge`() {
        val acca = buildAccumulator(listOf(leg(2.0, 10, "a"), leg(2.0, 10, "b")))!!
        // market implied 0.5 each -> joint 0.25; model 0.6 each -> joint 0.36.
        assertEquals(0.25, acca.jointMarketProb, 1e-9)
        assertEquals(0.36, acca.jointModelProb, 1e-9)
        assertEquals(11, acca.edgePercent) // (0.36 - 0.25) * 100
        assertTrue("edge should size a positive Kelly stake", acca.kellyStakeFraction > 0.0)
    }

    @Test
    fun `kelly stake is capped at a quarter of the bankroll`() {
        // Huge edge that would blow past the cap without clamping.
        val acca = buildAccumulator(listOf(leg(5.0, 60, "a"), leg(5.0, 60, "b")))!!
        assertTrue("stake must not exceed the 25% cap", acca.kellyStakeFraction <= 0.25 + 1e-9)
    }
}
