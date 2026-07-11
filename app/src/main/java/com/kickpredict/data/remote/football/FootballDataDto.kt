package com.kickpredict.data.remote.football

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire formats for the fields we consume from football-data.org v4. Everything else in the payload
 * is ignored (the shared Json is configured with ignoreUnknownKeys). See
 * https://www.football-data.org/documentation/quickstart .
 */
@Serializable
data class FdMatchesResponse(
    @SerialName("matches") val matches: List<FdMatch> = emptyList(),
)

@Serializable
data class FdMatch(
    @SerialName("id") val id: Long,
    @SerialName("utcDate") val utcDate: String, // ISO-8601 with 'Z', e.g. 2026-08-15T14:00:00Z
    @SerialName("status") val status: String,   // SCHEDULED / TIMED / IN_PLAY / FINISHED / ...
    @SerialName("matchday") val matchday: Int? = null,
    @SerialName("homeTeam") val homeTeam: FdTeam,
    @SerialName("awayTeam") val awayTeam: FdTeam,
)

@Serializable
data class FdTeam(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("shortName") val shortName: String? = null,
    @SerialName("tla") val tla: String? = null,
)

@Serializable
data class FdStandingsResponse(
    @SerialName("standings") val standings: List<FdStandingGroup> = emptyList(),
)

@Serializable
data class FdStandingGroup(
    @SerialName("type") val type: String = "TOTAL",
    @SerialName("table") val table: List<FdStandingRow> = emptyList(),
)

@Serializable
data class FdStandingRow(
    @SerialName("position") val position: Int,
    @SerialName("team") val team: FdTeam,
    @SerialName("playedGames") val playedGames: Int = 0,
    @SerialName("won") val won: Int = 0,
    @SerialName("draw") val draw: Int = 0,
    @SerialName("lost") val lost: Int = 0,
    @SerialName("points") val points: Int = 0,
    @SerialName("goalsFor") val goalsFor: Int = 0,
    @SerialName("goalsAgainst") val goalsAgainst: Int = 0,
    @SerialName("form") val form: String? = null, // e.g. "W,W,D,L,W" (most recent last)
)
