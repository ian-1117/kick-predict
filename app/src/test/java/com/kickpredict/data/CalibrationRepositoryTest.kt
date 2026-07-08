package com.kickpredict.data

import com.kickpredict.data.local.dao.MatchResultDao
import com.kickpredict.data.local.dao.PredictionLogDao
import com.kickpredict.data.local.entity.MatchResultEntity
import com.kickpredict.data.local.entity.PredictionLogEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.domain.model.PredictionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationRepositoryTest {

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

    private fun matchWith(prediction: PredictionResult) =
        MockDataProvider.matches().first().copy(predictedResult = prediction)

    private fun prediction(home: Int, draw: Int, away: Int, confidence: Int) =
        PredictionResult(home, draw, away, confidence, rationale = emptyList())

    @Test
    fun `records a prediction and scores it correct when the outcome matches`() = runBlocking {
        val repo = repo()
        val match = matchWith(prediction(home = 70, draw = 20, away = 10, confidence = 82)) // predicts HOME_WIN

        repo.recordPredictions(listOf(match))
        repo.recordResult(match.id, homeGoals = 2, awayGoals = 0) // real home win

        assertEquals(1, repo.recordedResultCount())
        val records = repo.predictionRecords()
        assertEquals(1, records.size)
        assertEquals(82, records[0].confidence)
        assertTrue("home-win prediction should score correct", records[0].wasCorrect)

        val history = repo.history()
        assertEquals(1, history.size)
        assertEquals(2, history[0].homeGoals)
        assertEquals(0, history[0].awayGoals)
    }

    @Test
    fun `scores a prediction incorrect when the outcome differs`() = runBlocking {
        val repo = repo()
        val match = matchWith(prediction(home = 20, draw = 20, away = 60, confidence = 55)) // predicts AWAY_WIN

        repo.recordPredictions(listOf(match))
        repo.recordResult(match.id, homeGoals = 1, awayGoals = 1) // real draw

        assertFalse(repo.predictionRecords()[0].wasCorrect)
    }

    @Test
    fun `results without a logged prediction are ignored`() = runBlocking {
        val repo = repo()
        repo.recordResult("unknown_match", 1, 0)
        assertTrue(repo.predictionRecords().isEmpty())
        assertTrue(repo.history().isEmpty())
        assertEquals(1, repo.recordedResultCount())
    }
}
