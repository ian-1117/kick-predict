package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.standings.LeagueTable
import com.kickpredict.domain.standings.TableEntry

/**
 * Builds a league table for each league from the recorded results (see [LeagueTable] for the rules).
 */
class GetStandingsUseCase(
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(): Map<LeagueType, List<Standing>> =
        calibrationRepository.recordedResults()
            .groupBy { it.league }
            .mapValues { (_, results) ->
                LeagueTable.build(
                    results.map { r ->
                        TableEntry(r.homeTeamId, r.homeTeam, r.awayTeamId, r.awayTeam, r.homeGoals, r.awayGoals)
                    },
                )
            }
}
