package com.kickpredict.domain.usecase

import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.blendedWithMarket
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository

/**
 * Loads a single fixture by id, attaches its (market-tempered) prediction, and logs it for calibration.
 */
class GetPredictedMatchUseCase(
    private val repository: MatchRepository,
    private val engine: PredictionEngine,
    private val calibrationRepository: CalibrationRepository,
    private val odds: () -> Map<String, MarketOdds> = { emptyMap() },
) {
    suspend operator fun invoke(id: String): Match? {
        val market = runCatching { odds() }.getOrDefault(emptyMap())
        val match = repository.getMatch(id)?.let {
            val p = engine.predict(it)
            it.copy(predictedResult = market[it.id]?.let { o -> p.blendedWithMarket(o) } ?: p)
        }
        if (match != null) runCatching { calibrationRepository.recordPredictions(listOf(match)) }
        return match
    }
}
