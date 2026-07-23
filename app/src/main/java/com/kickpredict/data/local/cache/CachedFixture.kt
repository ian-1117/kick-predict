package com.kickpredict.data.local.cache

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
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Serializable snapshot of a fixture, persisted as JSON so the app opens on the last known fixtures
 * instantly (then refreshes). Kept in the data layer so the domain models stay serialization-free.
 * The prediction is intentionally omitted — it's recomputed on load.
 */
@Serializable
data class CachedMatch(
    val id: String,
    val league: String,
    val home: CachedTeam,
    val away: CachedTeam,
    val kickoff: String,
    val venue: String,
    val h2h: List<CachedMeeting>,
    val round: Int,
    val ctx: CachedContext,
)

@Serializable
data class CachedTeam(
    val id: String,
    val name: String,
    val shortName: String,
    val position: Int,
    val form: List<String>,
    val rating: Double,
    val goalsFor: Double,
    val goalsAgainst: Double,
    val daysSince: Int,
    val korean: String,
    val crestPrimary: Long,
    val crestSecondary: Long,
)

@Serializable
data class CachedMeeting(val outcome: String, val atHomeVenue: Boolean)

@Serializable
data class CachedContext(
    val homeInjured: Int,
    val homeLineup: Int,
    val awayInjured: Int,
    val awayLineup: Int,
    val weather: String,
)

fun Match.toCached(): CachedMatch = CachedMatch(
    id = id,
    league = league.name,
    home = homeTeam.toCached(),
    away = awayTeam.toCached(),
    kickoff = kickoff.toString(),
    venue = venue,
    h2h = headToHead.recentMeetings.map { CachedMeeting(it.outcome.name, it.atHomeVenue) },
    round = round,
    ctx = CachedContext(
        context.homeAvailability.keyPlayersInjured, context.homeAvailability.lineupStrengthPercent,
        context.awayAvailability.keyPlayersInjured, context.awayAvailability.lineupStrengthPercent,
        context.weather.name,
    ),
)

private fun TeamProfile.toCached() = CachedTeam(
    id, name, shortName, leaguePosition, recentForm.map { it.name },
    overallRating, goalsScoredAvg, goalsConcededAvg, daysSinceLastMatch, koreanName, crestPrimary, crestSecondary,
)

fun CachedMatch.toDomain(): Match = Match(
    id = id,
    league = LeagueType.valueOf(league),
    homeTeam = home.toDomain(),
    awayTeam = away.toDomain(),
    kickoff = LocalDateTime.parse(kickoff),
    venue = venue,
    headToHead = HeadToHead(h2h.map { H2HMeeting(H2HOutcome.valueOf(it.outcome), it.atHomeVenue) }),
    round = round,
    context = MatchContext(
        homeAvailability = TeamAvailability(ctx.homeInjured, ctx.homeLineup),
        awayAvailability = TeamAvailability(ctx.awayInjured, ctx.awayLineup),
        weather = Weather.valueOf(ctx.weather),
    ),
)

private fun CachedTeam.toDomain() = TeamProfile(
    id = id, name = name, shortName = shortName, leaguePosition = position,
    recentForm = form.map { MatchOutcome.valueOf(it) },
    overallRating = rating, goalsScoredAvg = goalsFor, goalsConcededAvg = goalsAgainst,
    daysSinceLastMatch = daysSince, koreanName = korean, crestPrimary = crestPrimary, crestSecondary = crestSecondary,
)
