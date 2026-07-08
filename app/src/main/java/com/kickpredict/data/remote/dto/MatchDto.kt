package com.kickpredict.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for a fixture as it would arrive from a real prediction/data API.
 * Kept intentionally close to [com.kickpredict.domain.model.Match] so mapping is trivial.
 */
@Serializable
data class MatchDto(
    @SerialName("id") val id: String,
    @SerialName("league") val league: String,
    @SerialName("kickoff") val kickoff: String, // ISO-8601
    @SerialName("venue") val venue: String,
    @SerialName("home") val home: TeamDto,
    @SerialName("away") val away: TeamDto,
    @SerialName("h2h") val headToHead: HeadToHeadDto,
    @SerialName("context") val context: MatchContextDto = MatchContextDto(),
)

@Serializable
data class MatchContextDto(
    @SerialName("home") val home: AvailabilityDto = AvailabilityDto(),
    @SerialName("away") val away: AvailabilityDto = AvailabilityDto(),
    @SerialName("weather") val weather: String = "CLEAR",
)

@Serializable
data class AvailabilityDto(
    @SerialName("injured") val keyPlayersInjured: Int = 0,
    @SerialName("lineup_pct") val lineupStrengthPercent: Int = 100,
)

@Serializable
data class TeamDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("short_name") val shortName: String,
    @SerialName("position") val leaguePosition: Int,
    @SerialName("recent_form") val recentForm: List<String>, // "W" / "D" / "L"
    @SerialName("rating") val overallRating: Double,
    @SerialName("goals_scored_avg") val goalsScoredAvg: Double,
    @SerialName("goals_conceded_avg") val goalsConcededAvg: Double,
    @SerialName("days_since_last_match") val daysSinceLastMatch: Int,
)

@Serializable
data class HeadToHeadDto(
    // Most-recent-first list of past meetings from the home team's perspective.
    @SerialName("recent_meetings") val recentMeetings: List<H2HMeetingDto> = emptyList(),
)

@Serializable
data class H2HMeetingDto(
    @SerialName("outcome") val outcome: String, // "H" / "D" / "A"
    @SerialName("at_home_venue") val atHomeVenue: Boolean = false,
)
