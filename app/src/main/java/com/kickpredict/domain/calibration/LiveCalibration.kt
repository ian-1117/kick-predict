package com.kickpredict.domain.calibration

import com.kickpredict.domain.model.CalibrationProvider
import com.kickpredict.domain.model.LeagueCalibration
import com.kickpredict.domain.model.LeagueType

/**
 * A [ConfidenceCalibration] whose backing curve can be swapped at runtime. The engine holds this
 * instance and reads through it on every prediction, so refitting from newly recorded results takes
 * effect immediately without rebuilding the engine. Defaults to identity (no calibration).
 */
class MutableConfidenceCalibration(
    initial: ConfidenceCalibration = IdentityConfidenceCalibration,
) : ConfidenceCalibration {

    @Volatile
    private var delegate: ConfidenceCalibration = initial

    override fun calibrate(rawConfidence: Int): Int = delegate.calibrate(rawConfidence)

    fun update(calibration: ConfidenceCalibration) {
        delegate = calibration
    }

    fun reset() {
        delegate = IdentityConfidenceCalibration
    }
}

/**
 * A [CalibrationProvider] that overlays runtime-fitted per-league calibrations on top of the shipped
 * [CalibrationDefaults]. Leagues without a fitted calibration fall back to the defaults.
 */
class MutableCalibrationProvider(
    initial: Map<LeagueType, LeagueCalibration> = emptyMap(),
) : CalibrationProvider {

    @Volatile
    private var overrides: Map<LeagueType, LeagueCalibration> = initial

    override fun forLeague(league: LeagueType): LeagueCalibration =
        overrides[league] ?: CalibrationDefaults.forLeague(league)

    fun update(calibrations: Map<LeagueType, LeagueCalibration>) {
        overrides = calibrations
    }
}
