package com.kickpredict.data.remote.apisports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire formats for API-Sports (v3.football.api-sports.io), the K League source. Every payload is
 * wrapped in a `response` array; auth is the `x-apisports-key` header (see NetworkModule). Unknown
 * fields are ignored. Free tier: 100 requests/day, current season, all leagues incl. K League.
 * See https://www.api-football.com/documentation-v3 .
 */

@Serializable
data class AsFixturesResponse(@SerialName("response") val response: List<AsFixture> = emptyList())

@Serializable
data class AsFixture(
    @SerialName("fixture") val fixture: AsFixtureInfo,
    @SerialName("league") val league: AsFixtureLeague = AsFixtureLeague(),
    @SerialName("teams") val teams: AsTeams,
    @SerialName("goals") val goals: AsGoals = AsGoals(),
)

@Serializable
data class AsFixtureInfo(
    @SerialName("id") val id: Long,
    @SerialName("date") val date: String = "",          // ISO 8601 with offset, e.g. 2026-03-01T06:00:00+00:00
    @SerialName("status") val status: AsStatus = AsStatus(),
    @SerialName("venue") val venue: AsVenue = AsVenue(),
)

/** Match status short code: NS (not started), 1H/HT/2H/ET/P (live), FT/AET/PEN (finished), … */
@Serializable
data class AsStatus(@SerialName("short") val short: String = "")

@Serializable
data class AsVenue(@SerialName("name") val name: String? = null)

@Serializable
data class AsFixtureLeague(@SerialName("round") val round: String = "")

@Serializable
data class AsTeams(@SerialName("home") val home: AsTeam, @SerialName("away") val away: AsTeam)

@Serializable
data class AsTeam(@SerialName("id") val id: Long, @SerialName("name") val name: String = "")

@Serializable
data class AsGoals(@SerialName("home") val home: Int? = null, @SerialName("away") val away: Int? = null)

// --- Standings ---

@Serializable
data class AsStandingsResponse(@SerialName("response") val response: List<AsStandingsWrap> = emptyList())

@Serializable
data class AsStandingsWrap(@SerialName("league") val league: AsStandingsLeague = AsStandingsLeague())

/** `standings` is a list of groups (one for a single-table league), each a list of rows. */
@Serializable
data class AsStandingsLeague(@SerialName("standings") val standings: List<List<AsStandingRow>> = emptyList())

@Serializable
data class AsStandingRow(
    @SerialName("rank") val rank: Int = 0,
    @SerialName("team") val team: AsTeam,
    @SerialName("points") val points: Int = 0,
    @SerialName("goalsDiff") val goalsDiff: Int = 0,
    @SerialName("form") val form: String? = null,       // e.g. "WWDLW" (oldest→newest)
    @SerialName("all") val all: AsStandingStats = AsStandingStats(),
)

@Serializable
data class AsStandingStats(
    @SerialName("played") val played: Int = 0,
    @SerialName("goals") val goals: AsStandingGoals = AsStandingGoals(),
)

@Serializable
data class AsStandingGoals(
    @SerialName("for") val goalsFor: Int = 0,
    @SerialName("against") val goalsAgainst: Int = 0,
)
