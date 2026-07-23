package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.DriftWindow
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.ModelScorecard
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.brierDrift
import com.kickpredict.domain.model.computeScorecard
import com.kickpredict.domain.rating.EloModel
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

/** Prediction accuracy for one round, for the accuracy-trend chart. */
data class RoundAccuracy(
    val round: Int,
    val total: Int,
    val correct: Int,
) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

/**
 * The model's record in one league — how sharp it is where. Hit rate is the headline; Brier reads
 * through lucky calls and rewards honest probabilities, so a league can have a decent hit rate yet a
 * poor Brier if the wins came from coin-flips.
 */
data class LeagueScore(
    val league: LeagueType,
    val count: Int,
    val hitRate: Double, // 0..1
    val brierScore: Double,
)

/** One predictor's out-of-sample accuracy over the recorded results. */
/** Which model a [ModelScore] describes; the display name is resolved in the UI layer. */
enum class ModelKind { ENGINE, ELO, BASELINE }

data class ModelScore(
    val kind: ModelKind,
    val correct: Int,
    val total: Int,
) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

/** Everything the calibration dashboard renders. */
data class CalibrationDashboard(
    val totalResults: Int,
    val overallHitRate: Double, // 0..1
    val status: CalibrationStatus,
    val reliability: List<ReliabilityBucket>,
    val accuracyByRound: List<RoundAccuracy>,
    val modelComparison: List<ModelScore>,
    val scorecard: ModelScorecard?,
    val byLeague: List<LeagueScore>,
    val drift: List<DriftWindow>,
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
        val byRound = results
            .filter { it.round > 0 }
            .groupBy { it.round }
            .toSortedMap()
            .map { (round, rs) -> RoundAccuracy(round, rs.size, rs.count { it.wasCorrect }) }
        val samples = calibrationRepository.scoringSamples()
        val byLeague = results
            .groupBy { it.league }
            .map { (league, rs) ->
                // Brier reads through lucky calls; fall back to the hit-rate proxy if a league has no
                // logged probability vectors (e.g. K League fixtures logged before the vector existed).
                val brier = computeScorecard(samples.filter { it.league == league })?.brierScore
                    ?: (1.0 - rs.count { it.wasCorrect }.toDouble() / rs.size)
                LeagueScore(
                    league = league,
                    count = rs.size,
                    hitRate = rs.count { it.wasCorrect }.toDouble() / rs.size,
                    brierScore = brier,
                )
            }
            .sortedByDescending { it.count }
        return CalibrationDashboard(
            modelComparison = compareModels(results),
            totalResults = results.size,
            overallHitRate = overall,
            status = status,
            reliability = buckets,
            accuracyByRound = byRound,
            scorecard = computeScorecard(samples),
            byLeague = byLeague,
            drift = brierDrift(samples),
            recent = results.take(30),
        )
    }

    /**
     * Out-of-sample accuracy of three predictors over the recorded results:
     *  - the full AI engine (its logged prediction),
     *  - a learned Elo model evaluated **walk-forward** (predict each match, then train on it),
     *  - a naive baseline that always backs the home side.
     */
    private fun compareModels(results: List<RecordedResult>): List<ModelScore> {
        if (results.isEmpty()) return emptyList()
        val ordered = results.sortedWith(compareBy({ it.round }, { it.recordedAt }))
        val elo = EloModel()
        var engineCorrect = 0
        var eloCorrect = 0
        var homeCorrect = 0
        ordered.forEach { r ->
            if (r.wasCorrect) engineCorrect++
            val (pH, pD, pA) = elo.predict(r.homeTeamId, r.awayTeamId)
            val eloPick = when (maxOf(pH, pD, pA)) {
                pH -> PredictedOutcome.HOME_WIN
                pA -> PredictedOutcome.AWAY_WIN
                else -> PredictedOutcome.DRAW
            }
            if (eloPick == r.actualOutcome) eloCorrect++
            if (r.actualOutcome == PredictedOutcome.HOME_WIN) homeCorrect++
            elo.update(r.homeTeamId, r.awayTeamId, r.homeGoals, r.awayGoals)
        }
        val n = ordered.size
        return listOf(
            ModelScore(ModelKind.ENGINE, engineCorrect, n),
            ModelScore(ModelKind.ELO, eloCorrect, n),
            ModelScore(ModelKind.BASELINE, homeCorrect, n),
        )
    }
}
