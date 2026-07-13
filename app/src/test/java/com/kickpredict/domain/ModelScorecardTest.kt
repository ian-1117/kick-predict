package com.kickpredict.domain

import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ScoringSample
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
}
