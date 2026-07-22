package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.standings.LeagueTable
import com.kickpredict.domain.standings.TableEntry

/**
 * Builds each league's table from recorded results (see [LeagueTable] for the rules), scoped to the
 * **current season**: only results for fixtures in the currently-loaded fixture set count. Each
 * league's live feed returns its own season window, so this keeps every table on that league's
 * current campaign and drops any stale results left over from an earlier season or import.
 */
class GetStandingsUseCase(
    private val matchRepository: MatchRepository,
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(): Map<LeagueType, List<Standing>> {
        val currentSeasonIds = runCatching { matchRepository.cachedMatches() ?: matchRepository.getMatches() }
            .getOrDefault(emptyList())
            .map { it.id }
            .toSet()
        return calibrationRepository.recordedResults()
            .filter { it.matchId in currentSeasonIds }
            .groupBy { it.league }
            .mapValues { (_, results) ->
                LeagueTable.build(
                    results.map { r ->
                        TableEntry(r.homeTeamId, r.homeTeam, r.awayTeamId, r.awayTeam, r.homeGoals, r.awayGoals)
                    },
                )
            }
    }
}
