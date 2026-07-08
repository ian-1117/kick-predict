package com.kickpredict.domain.usecase

import com.kickpredict.domain.calibration.Calibrator
import com.kickpredict.domain.calibration.ConfidenceCalibrator
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.repository.CalibrationRepository

/** Summary of the last recalibration, for surfacing "보정 현황" in the UI. */
data class CalibrationStatus(
    val recordedResults: Int,
    val confidenceApplied: Boolean,
    val leaguesCalibrated: List<LeagueType>,
)

/**
 * Refits both calibrators from accumulated results and injects them into the live engine holders.
 * Guarded by minimum-sample thresholds so a handful of results can't distort predictions — below the
 * threshold the engine keeps identity confidence / default league scoring.
 */
class RecalibrateUseCase(
    private val calibrationRepository: CalibrationRepository,
    private val confidenceCalibration: MutableConfidenceCalibration,
    private val calibrationProvider: MutableCalibrationProvider,
    private val minConfidenceSamples: Int = 20,
    private val minLeagueSamples: Int = 10,
) {
    suspend operator fun invoke(): CalibrationStatus {
        val records = calibrationRepository.predictionRecords()
        val confidenceApplied = records.size >= minConfidenceSamples
        if (confidenceApplied) {
            confidenceCalibration.update(ConfidenceCalibrator.fit(records))
        } else {
            confidenceCalibration.reset()
        }

        val leagueCalibrations = calibrationRepository.history()
            .groupBy { it.league }
            .filterValues { it.size >= minLeagueSamples }
            .mapValues { (_, games) -> Calibrator.calibrateLeague(games) }
        calibrationProvider.update(leagueCalibrations)

        return CalibrationStatus(
            recordedResults = calibrationRepository.recordedResultCount(),
            confidenceApplied = confidenceApplied,
            leaguesCalibrated = leagueCalibrations.keys.toList(),
        )
    }
}
