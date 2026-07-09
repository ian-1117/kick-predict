package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.repository.CalibrationRepository

/**
 * Builds a league table for each league from the recorded results (standard 3-1-0 points, ranked by
 * points, then goal difference, then goals for).
 */
class GetStandingsUseCase(
    private val calibrationRepository: CalibrationRepository,
) {
    suspend operator fun invoke(): Map<LeagueType, List<Standing>> =
        calibrationRepository.recordedResults()
            .groupBy { it.league }
            .mapValues { (_, results) -> buildTable(results) }

    private fun buildTable(results: List<RecordedResult>): List<Standing> {
        val table = mutableMapOf<String, Acc>()
        fun accFor(id: String, name: String) = table.getOrPut(id) { Acc(name) }.also { it.name = name }

        results.forEach { r ->
            val home = accFor(r.homeTeamId, r.homeTeam)
            val away = accFor(r.awayTeamId, r.awayTeam)
            home.played++; away.played++
            home.goalsFor += r.homeGoals; home.goalsAgainst += r.awayGoals
            away.goalsFor += r.awayGoals; away.goalsAgainst += r.homeGoals
            when {
                r.homeGoals > r.awayGoals -> { home.won++; away.lost++ }
                r.homeGoals < r.awayGoals -> { away.won++; home.lost++ }
                else -> { home.drawn++; away.drawn++ }
            }
        }

        return table.map { (id, a) ->
            Standing(id, a.name, a.played, a.won, a.drawn, a.lost, a.goalsFor, a.goalsAgainst)
        }.sortedWith(
            compareByDescending<Standing> { it.points }
                .thenByDescending { it.goalDiff }
                .thenByDescending { it.goalsFor },
        )
    }

    private class Acc(
        var name: String,
        var played: Int = 0,
        var won: Int = 0,
        var drawn: Int = 0,
        var lost: Int = 0,
        var goalsFor: Int = 0,
        var goalsAgainst: Int = 0,
    )
}
