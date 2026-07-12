package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.ValuePickStatus
import com.kickpredict.domain.model.ValuePicksReport
import com.kickpredict.domain.model.buildValuePicksReport
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.ValuePickRepository
import java.time.ZoneId

/**
 * The value-pick hub's engine: logs newly-flagged picks (capturing the flag-time price) and rolls the
 * ledger up into a settled report — realized ROI, and each pick tagged won / lost / pending.
 */
class GetValuePicksUseCase(
    private val valuePickRepository: ValuePickRepository,
    private val calibrationRepository: CalibrationRepository,
) {

    /** Log any fixtures the model currently flags as value (model edge ≥ threshold) that aren't yet in the ledger. */
    suspend fun record(matches: List<Match>, odds: Map<String, MarketOdds>) {
        if (odds.isEmpty()) return
        val picks = matches.mapNotNull { match ->
            val prediction = match.predictedResult ?: return@mapNotNull null
            val market = odds[match.id] ?: return@mapNotNull null
            val outcome = prediction.predictedOutcome
            val edge = modelPercent(prediction, outcome) - market.percentFor(outcome)
            if (edge < VALUE_EDGE_THRESHOLD) return@mapNotNull null
            ValuePick(
                matchId = match.id,
                league = match.league,
                homeTeam = match.homeTeam.shortName,
                awayTeam = match.awayTeam.shortName,
                round = match.round,
                pickedOutcome = outcome,
                edge = edge,
                odds = market.oddsFor(outcome),
                kickoffEpochMillis = match.kickoff.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
        }
        valuePickRepository.record(picks)
    }

    /** Settle the ledger against recorded results and roll it up, strongest edge first. */
    suspend fun report(): ValuePicksReport {
        val results = runCatching { calibrationRepository.recordedResults().associateBy { it.matchId } }
            .getOrDefault(emptyMap())
        val picks = valuePickRepository.all().map { pick ->
            val actual = results[pick.matchId]?.actualOutcome
            val status = when {
                actual == null -> ValuePickStatus.PENDING
                actual == pick.pickedOutcome -> ValuePickStatus.WON
                else -> ValuePickStatus.LOST
            }
            pick.copy(status = status)
        }
        return buildValuePicksReport(picks)
    }

    companion object {
        /** Model must beat the market by at least this many points to be logged as value. */
        const val VALUE_EDGE_THRESHOLD = 4

        private fun modelPercent(p: PredictionResult, outcome: PredictedOutcome): Int = when (outcome) {
            PredictedOutcome.HOME_WIN -> p.homeWinPercent
            PredictedOutcome.AWAY_WIN -> p.awayWinPercent
            PredictedOutcome.DRAW -> p.drawPercent
        }
    }
}
