package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.RecordedResult
import com.kickpredict.domain.model.Standing
import com.kickpredict.domain.rating.MutableEloProvider
import com.kickpredict.domain.repository.CalibrationRepository
import kotlin.math.roundToInt

/** Aggregated team page: identity, table position, learned Elo, recent results and fixtures. */
data class TeamDetail(
    val teamId: String,
    val name: String,
    val shortName: String,
    val league: LeagueType,
    val crestPrimary: Long,
    val crestSecondary: Long,
    val eloRating: Int,
    val standing: Standing?,
    val standingRank: Int?,
    val recentResults: List<RecordedResult>,
    val fixtures: List<Match>,
)

class GetTeamUseCase(
    private val getPredictedMatches: GetPredictedMatchesUseCase,
    private val getStandings: GetStandingsUseCase,
    private val calibrationRepository: CalibrationRepository,
    private val eloProvider: MutableEloProvider,
) {
    suspend operator fun invoke(teamId: String): TeamDetail? {
        val matches = getPredictedMatches()
        val teamMatches = matches.filter { it.homeTeam.id == teamId || it.awayTeam.id == teamId }
        val sample = teamMatches.firstOrNull() ?: return null
        val team = if (sample.homeTeam.id == teamId) sample.homeTeam else sample.awayTeam

        val table = getStandings()[sample.league].orEmpty()
        val standing = table.firstOrNull { it.teamId == teamId }
        val rank = table.indexOfFirst { it.teamId == teamId }.takeIf { it >= 0 }?.plus(1)
        val recent = calibrationRepository.recordedResults()
            .filter { it.homeTeamId == teamId || it.awayTeamId == teamId }
            .take(10)

        return TeamDetail(
            teamId = teamId,
            name = team.displayName,
            shortName = team.shortName,
            league = sample.league,
            crestPrimary = team.crestPrimary,
            crestSecondary = team.crestSecondary,
            eloRating = eloProvider.rating(teamId).roundToInt(),
            standing = standing,
            standingRank = rank,
            recentResults = recent,
            fixtures = teamMatches.sortedBy { it.kickoff }.take(12),
        )
    }
}
