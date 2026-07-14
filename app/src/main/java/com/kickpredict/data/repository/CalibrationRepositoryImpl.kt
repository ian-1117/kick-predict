package com.kickpredict.data.repository

import com.kickpredict.data.local.dao.MatchResultDao
import com.kickpredict.data.local.dao.PredictionLogDao
import com.kickpredict.data.local.entity.MatchResultEntity
import com.kickpredict.data.local.entity.PredictionLogEntity
import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.ScoringSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalibrationRepositoryImpl(
    private val predictionLogDao: PredictionLogDao,
    private val matchResultDao: MatchResultDao,
) : com.kickpredict.domain.repository.CalibrationRepository {

    override suspend fun recordPredictions(matches: List<Match>) = withContext(Dispatchers.IO) {
        val logs = matches.mapNotNull { match ->
            val p = match.predictedResult ?: return@mapNotNull null
            PredictionLogEntity(
                matchId = match.id,
                league = match.league,
                round = match.round,
                homeTeamId = match.homeTeam.id,
                awayTeamId = match.awayTeam.id,
                homeTeam = match.homeTeam.displayName,
                awayTeam = match.awayTeam.displayName,
                predictedOutcome = p.predictedOutcome.name,
                confidence = p.confidenceScore,
                rawConfidence = p.rawConfidenceScore,
                homeWinPercent = p.homeWinPercent,
                drawPercent = p.drawPercent,
                awayWinPercent = p.awayWinPercent,
                expectedHomeGoals = p.expectedHomeGoals,
                expectedAwayGoals = p.expectedAwayGoals,
                predictedAt = System.currentTimeMillis(),
            )
        }
        if (logs.isNotEmpty()) predictionLogDao.upsertAll(logs)
    }

    override suspend fun recordResult(matchId: String, homeGoals: Int, awayGoals: Int) =
        withContext(Dispatchers.IO) {
            matchResultDao.upsert(
                MatchResultEntity(matchId, homeGoals, awayGoals, System.currentTimeMillis()),
            )
        }

    override suspend fun replaceResults(results: Map<String, Pair<Int, Int>>) =
        withContext(Dispatchers.IO) {
            matchResultDao.deleteAll()
            val now = System.currentTimeMillis()
            results.forEach { (matchId, score) ->
                matchResultDao.upsert(MatchResultEntity(matchId, score.first, score.second, now))
            }
        }

    override suspend fun getResult(matchId: String): ActualResult? = withContext(Dispatchers.IO) {
        matchResultDao.getById(matchId)?.let {
            ActualResult(it.matchId, it.homeGoals, it.awayGoals, it.recordedAt)
        }
    }

    override suspend fun recordedResultCount(): Int = withContext(Dispatchers.IO) {
        matchResultDao.count()
    }

    /**
     * Training pairs for the reliability curve. Deliberately reads [PredictionLogEntity.rawConfidence]
     * rather than the displayed [PredictionLogEntity.confidence]: the curve maps raw → observed hit
     * rate, so feeding it a score the curve has already touched makes each refit undo the last one.
     */
    override suspend fun predictionRecords(): List<PredictionRecord> = withContext(Dispatchers.IO) {
        val logs = predictionLogDao.getAll().associateBy { it.matchId }
        matchResultDao.getAll().mapNotNull { result ->
            val log = logs[result.matchId] ?: return@mapNotNull null
            val actual = outcomeOf(result.homeGoals, result.awayGoals)
            PredictionRecord(
                confidence = log.rawConfidence,
                wasCorrect = log.predictedOutcome == actual.name,
            )
        }
    }

    override suspend fun recordedResults(): List<RecordedResult> = withContext(Dispatchers.IO) {
        val logs = predictionLogDao.getAll().associateBy { it.matchId }
        matchResultDao.getAll().mapNotNull { result ->
            val log = logs[result.matchId] ?: return@mapNotNull null
            RecordedResult(
                matchId = result.matchId,
                league = log.league,
                homeTeamId = log.homeTeamId,
                awayTeamId = log.awayTeamId,
                homeTeam = log.homeTeam,
                awayTeam = log.awayTeam,
                predictedOutcome = runCatching { PredictedOutcome.valueOf(log.predictedOutcome) }
                    .getOrDefault(PredictedOutcome.DRAW),
                confidence = log.confidence,
                homeGoals = result.homeGoals,
                awayGoals = result.awayGoals,
                recordedAt = result.recordedAt,
                round = log.round,
            )
        }.sortedByDescending { it.recordedAt }
    }

    override suspend fun scoringSamples(): List<ScoringSample> = withContext(Dispatchers.IO) {
        val logs = predictionLogDao.getAll().associateBy { it.matchId }
        matchResultDao.getAll().mapNotNull { result ->
            val log = logs[result.matchId] ?: return@mapNotNull null
            ScoringSample(
                homeWinPercent = log.homeWinPercent,
                drawPercent = log.drawPercent,
                awayWinPercent = log.awayWinPercent,
                actual = outcomeOf(result.homeGoals, result.awayGoals),
                league = log.league,
            )
        }
    }

    override suspend fun history(): List<HistoricalMatch> = withContext(Dispatchers.IO) {
        val logs = predictionLogDao.getAll().associateBy { it.matchId }
        matchResultDao.getAll().mapNotNull { result ->
            val league = logs[result.matchId]?.league ?: return@mapNotNull null
            HistoricalMatch(league, result.homeGoals, result.awayGoals)
        }
    }

    private fun outcomeOf(home: Int, away: Int): PredictedOutcome = when {
        home > away -> PredictedOutcome.HOME_WIN
        home < away -> PredictedOutcome.AWAY_WIN
        else -> PredictedOutcome.DRAW
    }
}
