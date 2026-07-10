package com.kickpredict.domain.simulation

import com.kickpredict.domain.model.LeagueType

/** Where one team is projected to finish, aggregated over every simulated season. */
data class TeamProjection(
    val teamId: String,
    val teamName: String,
    /** Position in the table as it stands at the cutoff, 1-based. */
    val currentRank: Int,
    val currentPoints: Int,
    val expectedPoints: Double,
    /** Mean simulated finishing position, 1-based. */
    val averageRank: Double,
    val titleProbability: Double,
    val continentalProbability: Double,
    val relegationProbability: Double,
    /** Where the team actually finished, if the real season is complete. */
    val actualRank: Int? = null,
) {
    val titlePercent: Int get() = percent(titleProbability)
    val continentalPercent: Int get() = percent(continentalProbability)
    val relegationPercent: Int get() = percent(relegationProbability)

    private fun percent(p: Double): Int = Math.round(p * 100).toInt()
}

/**
 * The outcome of simulating a league's remaining fixtures many times over.
 *
 * @param cutoffRound the first round that was simulated; every earlier round used its real result.
 *        A cutoff of 1 means the whole season was simulated (a preseason projection).
 */
data class SeasonProjection(
    val league: LeagueType,
    val cutoffRound: Int,
    val iterations: Int,
    val playedMatches: Int,
    val remainingMatches: Int,
    val rules: SeasonRules,
    /** Ranked by title probability, then expected points. */
    val teams: List<TeamProjection>,
) {
    val isComplete: Boolean get() = remainingMatches == 0
    val hasActualOutcome: Boolean get() = teams.any { it.actualRank != null }
}
