package com.kickpredict.domain

import com.kickpredict.data.local.dao.MatchResultDao
import com.kickpredict.data.local.dao.PredictionLogDao
import com.kickpredict.data.local.entity.MatchResultEntity
import com.kickpredict.data.local.entity.PredictionLogEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.rating.MutableEloProvider
import com.kickpredict.domain.usecase.RecalibrateUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check that recorded results train Elo and reach the engine via the shared provider. */
class EloTrainingTest {

    private class FakeLogDao : PredictionLogDao {
        val store = linkedMapOf<String, PredictionLogEntity>()
        override suspend fun upsertAll(logs: List<PredictionLogEntity>) { logs.forEach { store[it.matchId] = it } }
        override suspend fun getAll() = store.values.toList()
        override suspend fun getById(id: String) = store[id]
    }
    private class FakeResultDao : MatchResultDao {
        val store = linkedMapOf<String, MatchResultEntity>()
        override suspend fun upsert(result: MatchResultEntity) { store[result.matchId] = result }
        override suspend fun getById(id: String) = store[id]
        override suspend fun getAll() = store.values.toList()
        override suspend fun count() = store.size
        override suspend fun delete(id: String) { store.remove(id) }
        override suspend fun deleteAll() { store.clear() }
    }

    @Test
    fun `recorded results train Elo and change the engine's rating gap`() = runBlocking {
        val repo = CalibrationRepositoryImpl(FakeLogDao(), FakeResultDao())
        val engine = PredictionEngine()
        // Take a real fixture; log its prediction and a strong home win a few times.
        val match = MockDataProvider.matches().first { it.homeTeam.id != it.awayTeam.id }
        val predicted = match.copy(predictedResult = engine.predict(match))
        val homeId = match.homeTeam.id
        val awayId = match.awayTeam.id

        repo.recordPredictions(listOf(predicted))
        repo.recordResult(match.id, homeGoals = 3, awayGoals = 0)

        val eloProvider = MutableEloProvider()
        val recalibrate = RecalibrateUseCase(
            calibrationRepository = repo,
            confidenceCalibration = MutableConfidenceCalibration(),
            calibrationProvider = MutableCalibrationProvider(),
            eloProvider = eloProvider,
        )

        assertTrue("before training the gap is 0", eloProvider.ratingDiff(homeId, awayId) == 0.0)
        recalibrate()
        val diff = eloProvider.ratingDiff(homeId, awayId)
        println("ELO_TRAIN homeId=$homeId awayId=$awayId ratingDiff=$diff")
        assertTrue("home win should raise home rating above away", diff > 0.0)
    }
}
