package com.kickpredict.domain

import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.model.RationaleNote
import com.kickpredict.domain.model.blendedWithMarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketBlendTest {

    private fun prediction(home: Int, draw: Int, away: Int) = PredictionResult(
        homeWinPercent = home,
        drawPercent = draw,
        awayWinPercent = away,
        confidenceScore = 60,
        rationale = emptyList(),
    )

    @Test
    fun `blend pulls the model toward the market and still sums to 100`() {
        // Model loves the home side; the fair market (2.0/4.0/4.0 ≈ 50/25/25) is less sure.
        val blended = prediction(80, 12, 8).blendedWithMarket(MarketOdds(2.0, 4.0, 4.0), modelWeight = 0.5)
        assertEquals(100, blended.homeWinPercent + blended.drawPercent + blended.awayWinPercent)
        // Home probability moves down toward the market's ~50%.
        assertTrue(blended.homeWinPercent < 80)
        assertTrue(blended.homeWinPercent > 50)
        // Transparency note appended (market weight 50%).
        val note = blended.rationale.filterIsInstance<RationaleNote.MarketBlend>().single()
        assertEquals(50, note.marketWeightPercent)
    }

    @Test
    fun `a fully model-weighted blend leaves the split unchanged`() {
        val original = prediction(55, 25, 20)
        val blended = original.blendedWithMarket(MarketOdds(2.0, 4.0, 4.0), modelWeight = 1.0)
        assertEquals(original.homeWinPercent, blended.homeWinPercent)
        assertEquals(original.drawPercent, blended.drawPercent)
        assertEquals(original.awayWinPercent, blended.awayWinPercent)
    }

    @Test
    fun `blending can flip the predicted outcome toward the market favourite`() {
        // Model narrowly backs the draw; the market strongly favours the home side.
        val blended = prediction(30, 40, 30).blendedWithMarket(MarketOdds(1.3, 5.0, 9.0), modelWeight = 0.4)
        assertEquals(PredictedOutcome.HOME_WIN, blended.predictedOutcome)
    }
}
