package com.kickpredict.domain.usecase

import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.model.blendedWithMarket
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository

/**
 * Loads all fixtures, attaches a freshly computed prediction to each — tempered toward the market
 * price where odds exist — and logs those predictions (best-effort) for later calibration.
 */
class GetPredictedMatchesUseCase(
    private val repository: MatchRepository,
    private val engine: PredictionEngine,
    private val calibrationRepository: CalibrationRepository,
    private val odds: () -> Map<String, MarketOdds> = { emptyMap() },
    private val modelWeight: () -> Double = { 1.0 },
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<Match> {
        val matches = repository.getMatches(forceRefresh)
        val market = runCatching { odds() }.getOrDefault(emptyMap())
        val predicted = matches.map { match ->
            match.copy(predictedResult = predictBlended(match, market))
        }
        runCatching { calibrationRepository.recordPredictions(predicted) }
        return predicted
    }

    /** Predictions over the last persisted fixtures (no network) for an instant first paint. */
    suspend fun cached(): List<Match>? {
        val market = runCatching { odds() }.getOrDefault(emptyMap())
        return repository.cachedMatches()?.map { match -> match.copy(predictedResult = predictBlended(match, market)) }
    }

    private fun predictBlended(match: Match, market: Map<String, MarketOdds>): PredictionResult {
        val p = engine.predict(match)
        val weight = modelWeight().coerceIn(0.0, 1.0)
        val odds = market[match.id]
        return if (odds != null && weight < 1.0) p.blendedWithMarket(odds, weight) else p
    }
}
