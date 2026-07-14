package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.AccumulatorSummary
import com.kickpredict.domain.model.ParlayLeg
import com.kickpredict.domain.model.ParlaysReport
import com.kickpredict.domain.model.SavedParlay
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.model.buildParlaysReport
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.ParlayRepository
import java.util.UUID

/**
 * Saves parlays built in the accumulator and rolls the saved ledger up into a settled report — each
 * parlay tagged won / lost / pending against recorded results, plus the ROI over the settled ones.
 */
class GetParlaysUseCase(
    private val parlayRepository: ParlayRepository,
    private val calibrationRepository: CalibrationRepository,
) {

    /** Persist the current accumulator as a saved parlay, freezing its combined price at save time. */
    suspend fun save(summary: AccumulatorSummary, legs: List<ValuePick>, nowMillis: Long) {
        if (legs.size < 2) return
        parlayRepository.save(
            SavedParlay(
                id = UUID.randomUUID().toString(),
                legs = legs.map {
                    ParlayLeg(
                        matchId = it.matchId,
                        league = it.league,
                        homeTeam = it.homeTeam,
                        awayTeam = it.awayTeam,
                        pickedOutcome = it.pickedOutcome,
                        odds = it.odds,
                    )
                },
                comboOdds = summary.comboOdds,
                edgePercent = summary.edgePercent,
                kellyStakeFraction = summary.kellyStakeFraction,
                createdAt = nowMillis,
            ),
        )
    }

    suspend fun delete(id: String) = parlayRepository.delete(id)

    /** Settle the saved parlays against recorded results and roll them up. */
    suspend fun report(): ParlaysReport {
        val actuals = runCatching {
            calibrationRepository.recordedResults().associate { it.matchId to it.actualOutcome }
        }.getOrDefault(emptyMap())
        return buildParlaysReport(parlayRepository.all(), actuals)
    }
}
