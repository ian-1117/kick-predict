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
    suspend operator fun invoke(): List<Match> {
        val predicted = repository.getMatches().map { match ->
            match.copy(predictedResult = engine.predict(match))
        }
        runCatching { calibrationRepository.recordPredictions(predicted) }
        return predicted
    }
}
