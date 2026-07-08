package com.kickpredict.domain.repository

import com.kickpredict.domain.calibration.HistoricalMatch
import com.kickpredict.domain.calibration.PredictionRecord
import com.kickpredict.domain.model.ActualResult
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.RecordedResult

/**
 * Persists prediction snapshots + user-entered results and turns the accumulated data into
 * calibration inputs. This is the "record & store" end of the calibration feedback loop; refitting
 * the calibrators from [predictionRecords]/[history] and injecting them into the engine is a
 * follow-up step once enough results exist.
 */
interface CalibrationRepository {
    /** Log the current predictions (one row per match, latest wins). Best-effort. */
    suspend fun recordPredictions(matches: List<Match>)

    /** Save the actual final score entered by the user. */
    suspend fun recordResult(matchId: String, homeGoals: Int, awayGoals: Int)

    suspend fun getResult(matchId: String): ActualResult?

    suspend fun recordedResultCount(): Int

    /** All recorded results joined with their predictions, most recent first (for the dashboard). */
    suspend fun recordedResults(): List<RecordedResult>

    /** Confidence-vs-hit-rate pairs for [com.kickpredict.domain.calibration.ConfidenceCalibrator]. */
    suspend fun predictionRecords(): List<PredictionRecord>

    /** Actual results with their league for [com.kickpredict.domain.calibration.Calibrator]. */
    suspend fun history(): List<HistoricalMatch>
}
