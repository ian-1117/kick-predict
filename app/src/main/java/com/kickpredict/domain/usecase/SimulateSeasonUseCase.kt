package com.kickpredict.domain.usecase

import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.simulation.SeasonProjection
import com.kickpredict.domain.simulation.SeasonSimulator
import com.kickpredict.domain.standings.LeagueTable
import com.kickpredict.domain.standings.TableEntry

/**
 * The season's fixtures and whatever real results have been recorded for them, fetched once.
 *
 * These fixtures carry full-season profiles and are only used for things the result already settles:
 * the table at the cutoff, the round count, and the real final standing. The fixtures actually fed to
 * the simulator are rebuilt per cutoff by [SimulateSeasonUseCase.project].
 */
class SeasonData(
    val matchesByLeague: Map<LeagueType, List<Match>>,
    private val results: Map<String, Pair<Int, Int>>,
) {
    /** Leagues with at least one fixture, in enum order. */
    val leagues: List<LeagueType> get() = LeagueType.entries.filter { matchesByLeague[it]?.isNotEmpty() == true }

    fun lastRound(league: LeagueType): Int = matchesByLeague[league]?.maxOfOrNull { it.round } ?: 0

    fun resultFor(matchId: String): Pair<Int, Int>? = results[matchId]
}

/**
 * Projects how a league season finishes: real results up to [cutoffRound], simulated from there on.
 *
 * The remaining fixtures are re-fetched **as of the cutoff** and predicted fresh, so the model that
 * simulates round 19 onwards has only seen rounds 1–18. Predicting against the full-season fixtures
 * would leak the outcome into the very forecast being scored against it.
 */
class SimulateSeasonUseCase(
    private val repository: MatchRepository,
    private val engine: PredictionEngine,
    private val calibrationRepository: CalibrationRepository,
    private val simulator: SeasonSimulator,
) {

    suspend fun load(): SeasonData {
        val matches = repository.getMatches()
        val results = calibrationRepository.recordedResults().associate {
            it.matchId to (it.homeGoals to it.awayGoals)
        }
        return SeasonData(matches.groupBy { it.league }, results)
    }

    /**
     * @param cutoffRound the first round to simulate. 1 simulates the whole season (a preseason
     *        projection); a value past the last round leaves nothing to simulate.
     */
    suspend fun project(
        data: SeasonData,
        league: LeagueType,
        cutoffRound: Int,
        iterations: Int = SeasonSimulator.DEFAULT_ITERATIONS,
    ): SeasonProjection {
        val matches = data.matchesByLeague[league].orEmpty()

        // Rounds before the cutoff only count once their real result is in; a fixture we never
        // recorded is simply absent from the starting table rather than silently simulated.
        val played = matches.filter { it.round < cutoffRound }.mapNotNull { match ->
            data.resultFor(match.id)?.let { (homeGoals, awayGoals) -> match.toTableEntry(homeGoals, awayGoals) }
        }

        val remaining = repository.getMatchesAsOf(cutoffRound)
            .filter { it.league == league }
            .map { it.copy(predictedResult = engine.predict(it)) }

        return simulator.simulate(
            league = league,
            played = played,
            remaining = remaining,
            iterations = iterations,
            cutoffRound = cutoffRound,
            actualFinalRank = actualFinalRank(data, matches),
        )
    }

    /**
     * The real final table, when every fixture of the season has a recorded result — which is the
     * case for the bundled historical seasons. Lets the UI show what actually happened next to what
     * the model expected.
     */
    private fun actualFinalRank(data: SeasonData, matches: List<Match>): Map<String, Int> {
        val entries = matches.map { match ->
            val (homeGoals, awayGoals) = data.resultFor(match.id) ?: return emptyMap()
            match.toTableEntry(homeGoals, awayGoals)
        }
        if (entries.isEmpty()) return emptyMap()
        return LeagueTable.build(entries).withIndex().associate { (i, standing) -> standing.teamId to i + 1 }
    }

    private fun Match.toTableEntry(homeGoals: Int, awayGoals: Int) = TableEntry(
        homeTeamId = homeTeam.id,
        homeTeam = homeTeam.displayName,
        awayTeamId = awayTeam.id,
        awayTeam = awayTeam.displayName,
        homeGoals = homeGoals,
        awayGoals = awayGoals,
    )
}
