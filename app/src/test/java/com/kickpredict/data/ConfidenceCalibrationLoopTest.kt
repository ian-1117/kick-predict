package com.kickpredict.data

import com.kickpredict.data.local.dao.MatchResultDao
import com.kickpredict.data.local.dao.PredictionLogDao
import com.kickpredict.data.local.entity.MatchResultEntity
import com.kickpredict.data.local.entity.PredictionLogEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.mock.ResultSimulator
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.domain.calibration.ConfidenceCalibrator
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.repository.CalibrationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reliability curve maps a *raw* engine confidence to its observed hit rate. Fitting it against
 * scores it has already corrected makes every refit undo the previous one, and the displayed
 * confidence flips between two values on each refresh.
 */
class ConfidenceCalibrationLoopTest {

    private class FakePredictionLogDao : PredictionLogDao {
        val store = linkedMapOf<String, PredictionLogEntity>()
        override suspend fun upsertAll(logs: List<PredictionLogEntity>) { logs.forEach { store[it.matchId] = it } }
        override suspend fun getAll() = store.values.toList()
        override suspend fun getById(id: String) = store[id]
    }

    private class FakeMatchResultDao : MatchResultDao {
        val store = linkedMapOf<String, MatchResultEntity>()
        override suspend fun upsert(result: MatchResultEntity) { store[result.matchId] = result }
        override suspend fun getById(id: String) = store[id]
        override suspend fun getAll() = store.values.toList()
        override suspend fun count() = store.size
        override suspend fun delete(id: String) { store.remove(id) }
    }

    private fun repo() = CalibrationRepositoryImpl(FakePredictionLogDao(), FakeMatchResultDao())

    @Test
    fun `the curve is trained on the raw score, not the one already shown`() = runBlocking {
        val repo = repo()
        val match = MockDataProvider.matches().first().copy(
            predictedResult = PredictionResult(
                homeWinPercent = 70,
                drawPercent = 20,
                awayWinPercent = 10,
                confidenceScore = 40, // what the user saw, after the curve
                rawConfidenceScore = 70, // what the engine actually computed
                rationale = emptyList(),
            ),
        )

        repo.recordPredictions(listOf(match))
        repo.recordResult(match.id, homeGoals = 2, awayGoals = 0)

        assertEquals("calibration must train on the raw score", 70, repo.predictionRecords()[0].confidence)
        assertEquals("the dashboard still reports what was displayed", 40, repo.recordedResults()[0].confidence)
    }

    @Test
    fun `refitting repeatedly settles instead of flipping between two values`() = runBlocking {
        val calibration = MutableConfidenceCalibration()
        val engine = PredictionEngine(confidenceCalibration = calibration)
        val repo = repo()

        val fixtures = MockDataProvider.leagueSchedule(LeagueType.K_LEAGUE, rounds = 30)
        val tracked = fixtures.first().id

        // Seed a season's worth of predictions and their actual results.
        val seeded = fixtures.map { it.copy(predictedResult = engine.predict(it)) }
        repo.recordPredictions(seeded)
        seeded.forEach { match ->
            val (home, away) = ResultSimulator.simulate(match)
            repo.recordResult(match.id, home, away)
        }

        // Each generation is one app refresh: refit the curve, then re-predict and re-log — exactly
        // what MatchListViewModel.load() does on every resume.
        val shown = (1..6).map { generation(engine, calibration, repo, fixtures, tracked) }

        // Generation 1 applies the curve for the first time, so it may move. From then on the inputs
        // to the fit no longer change, so the displayed score must hold.
        val settled = shown.drop(1).distinct()
        assertEquals("confidence should settle to one value, saw $shown", 1, settled.size)
    }

    private suspend fun generation(
        engine: PredictionEngine,
        calibration: MutableConfidenceCalibration,
        repo: CalibrationRepository,
        fixtures: List<Match>,
        trackedId: String,
    ): Int {
        calibration.update(ConfidenceCalibrator.fit(repo.predictionRecords()))
        val predicted = fixtures.map { it.copy(predictedResult = engine.predict(it)) }
        repo.recordPredictions(predicted)
        return predicted.first { it.id == trackedId }.predictedResult!!.confidenceScore
    }
}
