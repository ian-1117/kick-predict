package com.kickpredict.domain.standings

import com.kickpredict.domain.model.Standing

/** One finished fixture, reduced to what a league table needs. */
data class TableEntry(
    val homeTeamId: String,
    val homeTeam: String,
    val awayTeamId: String,
    val awayTeam: String,
    val homeGoals: Int,
    val awayGoals: Int,
)

/**
 * Builds a league table from finished fixtures: standard 3-1-0 points, ranked by points, then goal
 * difference, then goals scored.
 *
 * Shared by [com.kickpredict.domain.usecase.GetStandingsUseCase] (the real table) and
 * [com.kickpredict.domain.simulation.SeasonSimulator] (the table each simulated season starts from),
 * so a projected standing is ordered by exactly the rules the displayed standing is.
 */
object LeagueTable {

    /** Comparator matching the displayed table's ordering; teams not separated by it keep input order. */
    val Ranking: Comparator<Standing> = compareByDescending<Standing> { it.points }
        .thenByDescending { it.goalDiff }
        .thenByDescending { it.goalsFor }

    fun build(entries: List<TableEntry>, includeTeams: Collection<Pair<String, String>> = emptyList()): List<Standing> {
        val table = LinkedHashMap<String, Accumulator>()
        fun accumulatorFor(id: String, name: String) = table.getOrPut(id) { Accumulator(name) }.also { it.name = name }

        // Teams with no fixtures yet (a projection from matchday 1) still belong in the table.
        includeTeams.forEach { (id, name) -> accumulatorFor(id, name) }

        entries.forEach { entry ->
            val home = accumulatorFor(entry.homeTeamId, entry.homeTeam)
            val away = accumulatorFor(entry.awayTeamId, entry.awayTeam)
            home.played++; away.played++
            home.goalsFor += entry.homeGoals; home.goalsAgainst += entry.awayGoals
            away.goalsFor += entry.awayGoals; away.goalsAgainst += entry.homeGoals
            when {
                entry.homeGoals > entry.awayGoals -> { home.won++; away.lost++ }
                entry.homeGoals < entry.awayGoals -> { away.won++; home.lost++ }
                else -> { home.drawn++; away.drawn++ }
            }
        }

        return table.map { (id, a) ->
            Standing(id, a.name, a.played, a.won, a.drawn, a.lost, a.goalsFor, a.goalsAgainst)
        }.sortedWith(Ranking)
    }

    private class Accumulator(
        var name: String,
        var played: Int = 0,
        var won: Int = 0,
        var drawn: Int = 0,
        var lost: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0,
    )
}
