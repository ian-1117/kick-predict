package com.kickpredict.domain

import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.usecase.RecalibrateUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecalibrateUseCaseTest {

    private class FakeCalibrationRepository(
        var records: List<PredictionRecord> = emptyList(),
        var hist: List<HistoricalMatch> = emptyList(),
    ) : CalibrationRepository {
        override suspend fun recordPredictions(matches: List<Match>) {}
        override suspend fun recordResult(matchId: String, homeGoals: Int, awayGoals: Int) {}
        override suspend fun getResult(matchId: String): ActualResult? = null
        override suspend fun recordedResultCount(): Int = records.size
        override suspend fun recordedResults(): List<com.kickpredict.domain.model.RecordedResult> = emptyList()
        override suspend fun predictionRecords(): List<PredictionRecord> = records
        override suspend fun history(): List<HistoricalMatch> = hist
    }

    @Test
    fun `below the sample threshold keeps identity confidence`() = runBlocking {
        val confidence = MutableConfidenceCalibration()
        val provider = MutableCalibrationProvider()
        val repo = FakeCalibrationRepository(records = List(5) { PredictionRecord(90, false) })

        val status = RecalibrateUseCase(repo, confidence, provider)()

        assertFalse(status.confidenceApplied)
        assertEquals(90, confidence.calibrate(90)) // unchanged
    }

    @Test
    fun `enough over-confident results pull the confidence curve down`() = runBlocking {
        val confidence = MutableConfidenceCalibration()
        val provider = MutableCalibrationProvider()
        val records = List(50) { PredictionRecord(90, it % 10 < 6) } + // 90% claimed, 60% right
            List(50) { PredictionRecord(50, it % 10 < 4) }
        val repo = FakeCalibrationRepository(records = records)

        val status = RecalibrateUseCase(repo, confidence, provider)()

        assertTrue(status.confidenceApplied)
        assertTrue("90 should be pulled down (was ${confidence.calibrate(90)})", confidence.calibrate(90) < 90)
    }

    @Test
    fun `a league with enough history gets recalibrated`() = runBlocking {
        val provider = MutableCalibrationProvider()
        val history = List(12) { HistoricalMatch(LeagueType.EPL, homeGoals = 2, awayGoals = 1) }
        val repo = FakeCalibrationRepository(hist = history)

        val status = RecalibrateUseCase(repo, MutableConfidenceCalibration(), provider)()

        assertTrue(LeagueType.EPL in status.leaguesCalibrated)
        assertEquals(2.0, provider.forLeague(LeagueType.EPL).homeGoalsAvg, 1e-6)
        // A league with no data still falls back to defaults.
        assertEquals(
            com.kickpredict.domain.calibration.CalibrationDefaults.forLeague(LeagueType.K_LEAGUE).homeGoalsAvg,
            provider.forLeague(LeagueType.K_LEAGUE).homeGoalsAvg,
            1e-6,
        )
    }
}
