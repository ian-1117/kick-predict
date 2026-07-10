package com.kickpredict.domain.simulation

import com.kickpredict.domain.model.CalibrationProvider
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.standings.LeagueTable
import com.kickpredict.domain.standings.TableEntry
import kotlin.random.Random

/**
 * Monte-Carlo projection of a league season.
 *
 * The table as it stands after the played fixtures is the starting point; every remaining fixture is
 * then replayed [iterations] times by drawing a scoreline from its [ScoreGrid] — the same
 * distribution the [com.kickpredict.domain.engine.PredictionEngine] derives its win/draw/away
 * numbers from. Counting how often each team finishes first, in a continental place, or in an
 * automatic relegation place turns those samples into probabilities.
 *
 * Fixtures are drawn independently: a simulated season does not feed its own results back into the
 * ratings, so a team on a hot streak does not compound. This understates the tails (real title races
 * are streakier than these samples) but keeps each fixture's marginal distribution honest.
 *
 * Deterministic for a given [seed], so the same cutoff always yields the same projection.
 */
class SeasonSimulator(
    private val calibration: CalibrationProvider,
) {

    fun simulate(
        league: LeagueType,
        played: List<TableEntry>,
        remaining: List<Match>,
        iterations: Int = DEFAULT_ITERATIONS,
        cutoffRound: Int = 1,
        actualFinalRank: Map<String, Int> = emptyMap(),
        seed: Long = DEFAULT_SEED,
    ): SeasonProjection {
        require(iterations > 0) { "iterations must be positive" }

        val names = LinkedHashMap<String, String>()
        played.forEach { names[it.homeTeamId] = it.homeTeam; names[it.awayTeamId] = it.awayTeam }
        remaining.forEach {
            names[it.homeTeam.id] = it.homeTeam.displayName
            names[it.awayTeam.id] = it.awayTeam.displayName
        }

        val baseTable = LeagueTable.build(played, includeTeams = names.map { it.key to it.value })
        val teamCount = baseTable.size
        val rules = SeasonRules.forLeague(league)
        val index = baseTable.withIndex().associate { (i, s) -> s.teamId to i }

        val basePoints = IntArray(teamCount) { baseTable[it].points }
        val baseGoalsFor = IntArray(teamCount) { baseTable[it].goalsFor }
        val baseGoalsAgainst = IntArray(teamCount) { baseTable[it].goalsAgainst }

        // Build every fixture's grid once; the iteration loop only draws from them.
        val fixtureCount = remaining.size
        val grids = arrayOfNulls<ScoreGrid>(fixtureCount)
        val homeIndex = IntArray(fixtureCount)
        val awayIndex = IntArray(fixtureCount)
        remaining.forEachIndexed { i, match ->
            val prediction = requireNotNull(match.predictedResult) {
                "match ${match.id} has no prediction; run it through the engine first"
            }
            grids[i] = ScoreGrid(
                lambdaHome = prediction.expectedHomeGoals,
                lambdaAway = prediction.expectedAwayGoals,
                drawInflation = calibration.forLeague(match.league).drawInflation,
            )
            homeIndex[i] = index.getValue(match.homeTeam.id)
            awayIndex[i] = index.getValue(match.awayTeam.id)
        }

        val titleCount = IntArray(teamCount)
        val continentalCount = IntArray(teamCount)
        val relegationCount = IntArray(teamCount)
        val pointsSum = LongArray(teamCount)
        val rankSum = LongArray(teamCount)

        val points = IntArray(teamCount)
        val goalsFor = IntArray(teamCount)
        val goalsAgainst = IntArray(teamCount)
        val order = Array(teamCount) { it }
        // Ranks the simulated table by the same rules the displayed one uses: points, GD, then GF.
        val ranking = Comparator<Int> { a, b ->
            var c = points[b] - points[a]
            if (c == 0) c = (goalsFor[b] - goalsAgainst[b]) - (goalsFor[a] - goalsAgainst[a])
            if (c == 0) c = goalsFor[b] - goalsFor[a]
            c
        }

        val random = Random(seed)
        val relegationFrom = teamCount - rules.relegationSpots

        repeat(iterations) {
            basePoints.copyInto(points)
            baseGoalsFor.copyInto(goalsFor)
            baseGoalsAgainst.copyInto(goalsAgainst)

            for (i in 0 until fixtureCount) {
                val packed = grids[i]!!.sample(random)
                val homeGoals = ScoreGrid.unpackHome(packed)
                val awayGoals = ScoreGrid.unpackAway(packed)
                val home = homeIndex[i]
                val away = awayIndex[i]
                goalsFor[home] += homeGoals; goalsAgainst[home] += awayGoals
                goalsFor[away] += awayGoals; goalsAgainst[away] += homeGoals
                when {
                    homeGoals > awayGoals -> points[home] += 3
                    homeGoals < awayGoals -> points[away] += 3
                    else -> { points[home]++; points[away]++ }
                }
            }

            for (i in 0 until teamCount) order[i] = i
            java.util.Arrays.sort(order, ranking)

            for (rank in 0 until teamCount) {
                val team = order[rank]
                rankSum[team] += (rank + 1).toLong()
                pointsSum[team] += points[team].toLong()
                if (rank == 0) titleCount[team]++
                if (rank < rules.continentalSpots) continentalCount[team]++
                if (rules.relegationSpots > 0 && rank >= relegationFrom) relegationCount[team]++
            }
        }

        val total = iterations.toDouble()
        val projections = baseTable.mapIndexed { i, standing ->
            TeamProjection(
                teamId = standing.teamId,
                teamName = standing.teamName,
                currentRank = i + 1,
                currentPoints = standing.points,
                expectedPoints = pointsSum[i] / total,
                averageRank = rankSum[i] / total,
                titleProbability = titleCount[i] / total,
                continentalProbability = continentalCount[i] / total,
                relegationProbability = relegationCount[i] / total,
                actualRank = actualFinalRank[standing.teamId],
            )
        }.sortedWith(
            compareByDescending<TeamProjection> { it.titleProbability }
                .thenByDescending { it.expectedPoints },
        )

        return SeasonProjection(
            league = league,
            cutoffRound = cutoffRound,
            iterations = iterations,
            playedMatches = played.size,
            remainingMatches = fixtureCount,
            rules = rules,
            teams = projections,
        )
    }

    companion object {
        const val DEFAULT_ITERATIONS = 10_000
        const val DEFAULT_SEED = 20240701L
    }
}
