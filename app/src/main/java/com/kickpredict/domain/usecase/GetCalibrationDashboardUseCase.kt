package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.repository.CalibrationRepository

/**
 * One confidence band's reliability: how often predictions in that band were actually right.
 * A well-calibrated model has [hitRate] ≈ [avgConfidence]/100.
 */
data class ReliabilityBucket(
    val rangeLabel: String,
    val count: Int,
    val avgConfidence: Double,
    val hitRate: Double, // 0..1
)

/** Everything the calibration dashboard renders. */
data class CalibrationDashboard(
    val totalResults: Int,
    val overallHitRate: Double, // 0..1
    val status: CalibrationStatus,
    val reliability: List<ReliabilityBucket>,
    val recent: List<RecordedResult>,
)

/**
 * Builds the calibration dashboard: refreshes calibration, then summarises accuracy, the
 * confidence-vs-reliability breakdown, and the recent recorded results.
 */
class GetCalibrationDashboardUseCase(
    private val calibrationRepository: CalibrationRepository,
    private val recalibrate: RecalibrateUseCase,
) {
    suspend operator fun invoke(): CalibrationDashboard {
        val status = recalibrate()
        val results = calibrationRepository.recordedResults()
        val overall = if (results.isEmpty()) 0.0 else results.count { it.wasCorrect }.toDouble() / results.size
        val buckets = results
            .groupBy { (it.confidence / 10).coerceIn(0, 9) }
            .toSortedMap()
            .map { (decile, rs) ->
                ReliabilityBucket(
                    rangeLabel = "${decile * 10}–${decile * 10 + 9}%",
                    count = rs.size,
                    avgConfidence = rs.map { it.confidence }.average(),
                    hitRate = rs.count { it.wasCorrect }.toDouble() / rs.size,
                )
            }
        return CalibrationDashboard(
            totalResults = results.size,
            overallHitRate = overall,
            status = status,
            reliability = buckets,
            recent = results.take(30),
        )
    }
}
