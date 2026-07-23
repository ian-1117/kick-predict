package com.kickpredict.domain.usecase

import com.kickpredict.domain.repository.CalibrationRepository

/** Saves the user-entered final score for a fixture (feeds calibration). */
class RecordMatchResultUseCase(
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(matchId: String, homeGoals: Int, awayGoals: Int) =
        calibrationRepository.recordResult(matchId, homeGoals, awayGoals)
}
