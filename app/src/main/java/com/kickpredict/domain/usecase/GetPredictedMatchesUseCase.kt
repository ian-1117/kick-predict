package com.kickpredict.domain.usecase

import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository

/**
 * Loads all fixtures, attaches a freshly computed prediction to each, and logs those predictions
 * (best-effort) so they can later be scored against real results for calibration.
 */
class GetPredictedMatchesUseCase(
    private val repository: MatchRepository,
    private val engine: PredictionEngine,
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<Match> {
        val predicted = repository.getMatches(forceRefresh).map { match ->
            match.copy(predictedResult = engine.predict(match))
        }
        runCatching { calibrationRepository.recordPredictions(predicted) }
        return predicted
    }

    /** Predictions over the last persisted fixtures (no network) for an instant first paint. */
    suspend fun cached(): List<Match>? =
        repository.cachedMatches()?.map { match -> match.copy(predictedResult = engine.predict(match)) }
}
