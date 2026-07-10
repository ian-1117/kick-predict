package com.kickpredict.domain.simulation

import com.kickpredict.domain.model.LeagueType

/**
 * How many places at each end of a league table matter, used to turn a simulated final position
 * into the probabilities the UI shows.
 *
 * Simplified on purpose: leagues that decide a place through a playoff (Bundesliga's 16th, K League
 * 1's 11th) are counted by their *automatic* spots only, so "강등 확률" reads as "finished in an
 * automatic relegation place".
 */
data class SeasonRules(
    /** Places qualifying for continental competition (UCL / ACL), from the top. */
    val continentalSpots: Int,
    /** Places automatically relegated, from the bottom. */
    val relegationSpots: Int,
) {
    companion object {
        fun forLeague(league: LeagueType): SeasonRules = when (league) {
            LeagueType.EPL -> SeasonRules(continentalSpots = 4, relegationSpots = 3)
            LeagueType.LALIGA -> SeasonRules(continentalSpots = 4, relegationSpots = 3)
            LeagueType.SERIE_A -> SeasonRules(continentalSpots = 4, relegationSpots = 3)
            LeagueType.BUNDESLIGA -> SeasonRules(continentalSpots = 4, relegationSpots = 2)
            LeagueType.K_LEAGUE -> SeasonRules(continentalSpots = 3, relegationSpots = 1)
            // Second tier: nothing below to fall into, and the top place is promotion rather than ACL.
            LeagueType.K_LEAGUE_2 -> SeasonRules(continentalSpots = 1, relegationSpots = 0)
        }
    }
}
