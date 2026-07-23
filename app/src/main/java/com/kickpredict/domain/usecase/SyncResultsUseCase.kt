package com.kickpredict.domain.usecase

import com.kickpredict.domain.repository.CalibrationRepository

/**
 * Seeds the results store with the current season's real final scores so standings, the accuracy
 * dashboard and the match cards reflect it. Prefers the live source's finished-match scores; falls
 * back to the bundled display-season results. Idempotent — safe to call on every load.
 *
 * Wired with plain lambdas so the domain layer stays unaware of the data sources.
 */
class SyncResultsUseCase(
    private val liveResults: () -> Map<String, Pair<Int, Int>>,
    private val bundledResults: () -> Map<String, Pair<Int, Int>>,
    private val liveConfigured: Boolean,
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke() {
        val results = liveResults().takeIf { liveConfigured && it.isNotEmpty() } ?: bundledResults()
        if (results.isNotEmpty()) calibrationRepository.replaceResults(results)
    }
}
