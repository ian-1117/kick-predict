package com.kickpredict.data.remote.apifootball

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire formats for the fields we consume from APIFootball (apiv3.apifootball.com), which carries the
 * two K League tiers (league ids 219 = K League 1, 218 = K League 2). Every value arrives as a
 * string; scores are blank until a match is played. Unknown fields (goalscorer, lineup, …) are
 * ignored. See https://apifootball.com/documentation/ .
 *
 * On error the API returns a `{ "error": 404, "message": ... }` object instead of an array, which
 * fails list deserialization — the caller treats that as "no live data" and falls back to bundled.
 */
@Serializable
data class AfEvent(
    @SerialName("match_id") val id: String,
    @SerialName("league_id") val leagueId: String = "",
    @SerialName("league_year") val leagueYear: String = "",
    @SerialName("match_date") val date: String = "",   // YYYY-MM-DD
    @SerialName("match_time") val time: String = "",   // HH:MM
    @SerialName("match_status") val status: String = "", // "Finished" / "Not Started" / "" / a minute
    @SerialName("match_live") val live: String = "0",     // "1" while the match is in play
    @SerialName("match_round") val round: String = "",
    @SerialName("match_stadium") val stadium: String = "",
    @SerialName("match_hometeam_id") val homeId: String = "",
    @SerialName("match_hometeam_name") val homeName: String = "",
    @SerialName("match_awayteam_id") val awayId: String = "",
    @SerialName("match_awayteam_name") val awayName: String = "",
    @SerialName("match_hometeam_score") val homeScore: String = "",
    @SerialName("match_awayteam_score") val awayScore: String = "",
)

/** One bookmaker's 1X2 odds for a match (get_odds). Blank when a market isn't offered. */
@Serializable
data class AfOdds(
    @SerialName("match_id") val matchId: String = "",
    @SerialName("odd_1") val home: String = "",   // decimal odds, home win
    @SerialName("odd_x") val draw: String = "",   // draw
    @SerialName("odd_2") val away: String = "",   // away win
)

@Serializable
data class AfStanding(
    @SerialName("team_id") val teamId: String = "",
    @SerialName("team_name") val teamName: String = "",
    @SerialName("overall_league_position") val position: String = "",
    @SerialName("overall_league_payed") val played: String = "", // note: API spells it "payed"
    @SerialName("overall_league_GF") val goalsFor: String = "",
    @SerialName("overall_league_GA") val goalsAgainst: String = "",
    @SerialName("overall_league_PTS") val points: String = "",
)
