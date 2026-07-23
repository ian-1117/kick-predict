package com.kickpredict.domain

import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ScoringSample
import com.kickpredict.domain.model.brierDrift
import com.kickpredict.domain.model.computeScorecard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class ModelScorecardTest {

    @Test
    fun `empty samples score to null`() {
        assertNull(computeScorecard(emptyList()))
    }

    @Test
    fun `a perfectly confident correct call scores near zero`() {
        val sc = computeScorecard(
            listOf(ScoringSample(homeWinPercent = 100, drawPercent = 0, awayWinPercent = 0, actual = PredictedOutcome.HOME_WIN)),
        )!!
        assertEquals(0.0, sc.brierScore, 1e-9)
        assertEquals(0.0, sc.logLoss, 1e-9)
        assertEquals(0.0, sc.calibrationError, 1e-9)
        assertEquals(1, sc.sampleCount)
    }

    @Test
    fun `brier and log-loss match the textbook formulas`() {
        // 60/30/10, actual = home win.
        val sc = computeScorecard(
            listOf(ScoringSample(homeWinPercent = 60, drawPercent = 30, awayWinPercent = 10, actual = PredictedOutcome.HOME_WIN)),
        )!!
        // Brier = (0.6-1)^2 + (0.3-0)^2 + (0.1-0)^2 = 0.16 + 0.09 + 0.01 = 0.26
        assertEquals(0.26, sc.brierScore, 1e-9)
        // Log-loss = -ln(0.6)
        assertEquals(-ln(0.6), sc.logLoss, 1e-9)
    }

    @Test
    fun `an overconfident wrong call is punished harder than a hedged one`() {
        val overconfident = computeScorecard(
            listOf(ScoringSample(90, 5, 5, PredictedOutcome.AWAY_WIN)),
        )!!
        val hedged = computeScorecard(
            listOf(ScoringSample(40, 30, 30, PredictedOutcome.AWAY_WIN)),
        )!!
        assertTrue(overconfident.logLoss > hedged.logLoss)
        assertTrue(overconfident.brierScore > hedged.brierScore)
    }

    @Test
    fun `brier drift needs enough history to form two windows`() {
        val few = List(15) { ScoringSample(60, 30, 10, PredictedOutcome.HOME_WIN, recordedAt = it.toLong()) }
        assertTrue("15 samples can't make two 10-wide windows", brierDrift(few).isEmpty())
    }

    @Test
    fun `brier drift splits chronologically and tracks a model that sharpens over time`() {
        // Oldest 20: confidently wrong (high Brier). Newest 20: confidently right (low Brier).
        val old = List(20) { ScoringSample(90, 5, 5, PredictedOutcome.AWAY_WIN, recordedAt = it.toLong()) }
        val new = List(20) { ScoringSample(90, 5, 5, PredictedOutcome.HOME_WIN, recordedAt = (100 + it).toLong()) }
        // Feed them out of order to prove the function sorts by recordedAt.
        val windows = brierDrift(new + old, maxWindows = 2, minPerWindow = 10)
        assertEquals(2, windows.size)
        assertEquals(20, windows.first().sampleCount)
        assertTrue("newest window should be sharper (lower Brier) than the oldest",
            windows.last().brierScore < windows.first().brierScore)
    }
}
