package com.kickpredict.domain.usecase

import com.kickpredict.data.mock.ResultSimulator
import com.kickpredict.domain.repository.CalibrationRepository

/**
 * Seeds simulated actual results for every fixture (all rounds), so the calibration loop has a full
 * history to learn from. Predictions are logged as a side effect of [getPredictedMatches]; then each
 * fixture gets a plausible actual scoreline recorded. Returns the number of results seeded.
 */
class SeedSampleResultsUseCase(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(): Int {
        val matches = getPredictedMatches() // also logs the predictions
        matches.forEach { match ->
            val (homeGoals, awayGoals) = ResultSimulator.simulate(match)
            calibrationRepository.recordResult(match.id, homeGoals, awayGoals)
        }
        return matches.size
    }
}
