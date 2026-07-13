package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match

/** A selectable team for the comparison picker. */
data class TeamRef(
    val id: String,
    val league: LeagueType,
    val name: String,
    val shortName: String,
)

/**
 * Powers the team-comparison screen: lists the teams available per league, and finds the fixture
 * between any two of them so their existing prediction + power comparison can be shown head to head.
 */
class CompareTeamsUseCase(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
) {
    /** Distinct teams, grouped by league and sorted by name — the picker's options. */
    suspend fun teamsByLeague(): Map<LeagueType, List<TeamRef>> =
        getPredictedMatches()
            .flatMap { m ->
                listOf(
                    TeamRef(m.homeTeam.id, m.league, m.homeTeam.displayName, m.homeTeam.shortName),
                    TeamRef(m.awayTeam.id, m.league, m.awayTeam.displayName, m.awayTeam.shortName),
                )
            }
            .distinctBy { it.id }
            .groupBy { it.league }
            .mapValues { (_, teams) -> teams.sortedBy { it.name } }

    /** The fixture between the two teams (preferring the one where [aId] is at home), or null. */
    suspend fun matchup(aId: String, bId: String): Match? {
        if (aId == bId) return null
        val matches = getPredictedMatches()
        return matches.firstOrNull { it.homeTeam.id == aId && it.awayTeam.id == bId }
            ?: matches.firstOrNull { it.homeTeam.id == bId && it.awayTeam.id == aId }
    }
}
