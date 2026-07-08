package com.kickpredict.domain.usecase

import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository

/**
 * Loads a single fixture by id, attaches its prediction, and logs it for calibration.
 */
class GetPredictedMatchUseCase(
    private val repository: MatchRepository,
    private val engine: PredictionEngine,
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(id: String): Match? {
        val match = repository.getMatch(id)?.let { it.copy(predictedResult = engine.predict(it)) }
        if (match != null) runCatching { calibrationRepository.recordPredictions(listOf(match)) }
        return match
    }
}
