package com.kickpredict.data.remote.dto

import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.TeamAvailability
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.model.Weather
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Maps the network [MatchDto] into the domain [Match]. */
fun MatchDto.toDomain(): Match = Match(
    id = id,
    league = LeagueType.valueOf(league),
    homeTeam = home.toDomain(),
    awayTeam = away.toDomain(),
    kickoff = LocalDateTime.parse(kickoff, DateTimeFormatter.ISO_DATE_TIME),
    venue = venue,
    headToHead = HeadToHead(
        headToHead.recentMeetings.map {
            H2HMeeting(
                outcome = when (it.outcome.uppercase()) {
                    "H" -> H2HOutcome.HOME_WIN
                    "A" -> H2HOutcome.AWAY_WIN
                    else -> H2HOutcome.DRAW
                },
                atHomeVenue = it.atHomeVenue,
            )
        },
    ),
    context = MatchContext(
        homeAvailability = TeamAvailability(context.home.keyPlayersInjured, context.home.lineupStrengthPercent),
        awayAvailability = TeamAvailability(context.away.keyPlayersInjured, context.away.lineupStrengthPercent),
        weather = runCatching { Weather.valueOf(context.weather.uppercase()) }.getOrDefault(Weather.CLEAR),
    ),
)

private fun TeamDto.toDomain(): TeamProfile = TeamProfile(
    id = id,
    name = name,
    shortName = shortName,
    leaguePosition = leaguePosition,
    recentForm = recentForm.map {
        when (it.uppercase()) {
            "W" -> MatchOutcome.WIN
            "D" -> MatchOutcome.DRAW
            else -> MatchOutcome.LOSS
        }
    },
    overallRating = overallRating,
    goalsScoredAvg = goalsScoredAvg,
    goalsConcededAvg = goalsConcededAvg,
    daysSinceLastMatch = daysSinceLastMatch,
)
