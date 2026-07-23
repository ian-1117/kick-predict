package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.standings.LeagueTable
import com.kickpredict.domain.standings.TableEntry

/**
 * The standings for every league plus which of them are showing **last season's** final table because
 * their current season hasn't kicked off yet (so the UI can label those).
 */
data class StandingsResult(
    val tables: Map<LeagueType, List<Standing>>,
    val previousSeasonLeagues: Set<LeagueType>,
)

/**
 * Builds each league's table. In-season leagues use the current season's recorded results, scoped to
 * the currently-loaded fixtures so stale results don't leak in. A league whose current season hasn't
 * started yet (no results) falls back to **last season's final table** from the bundled data, flagged
 * so the screen can say so. Each league's live feed runs its own calendar, so K League can be
 * mid-season while the European leagues are still in the off-season.
 */
class GetStandingsUseCase(
    private val matchRepository: MatchRepository,
    private val calibrationRepository: CalibrationRepository,
    private val previousSeasonStandings: () -> Map<LeagueType, List<Standing>> = { emptyMap() },
) {
    suspend operator fun invoke(): StandingsResult {
        val currentSeasonIds = runCatching { matchRepository.cachedMatches() ?: matchRepository.getMatches() }
            .getOrDefault(emptyList())
            .map { it.id }
            .toSet()
        val current = calibrationRepository.recordedResults()
            .filter { it.matchId in currentSeasonIds }
            .groupBy { it.league }
            .mapValues { (_, results) ->
                LeagueTable.build(
                    results.map { r ->
                        TableEntry(r.homeTeamId, r.homeTeam, r.awayTeamId, r.awayTeam, r.homeGoals, r.awayGoals)
                    },
                )
            }

        val previous = previousSeasonStandings()
        val tables = LinkedHashMap<LeagueType, List<Standing>>()
        val previousLeagues = mutableSetOf<LeagueType>()
        LeagueType.entries.forEach { league ->
            val inSeason = current[league]?.takeIf { it.isNotEmpty() }
            if (inSeason != null) {
                tables[league] = inSeason
            } else {
                previous[league]?.takeIf { it.isNotEmpty() }?.let {
                    tables[league] = it
                    previousLeagues += league
                }
            }
        }
        return StandingsResult(tables, previousLeagues)
    }
}
