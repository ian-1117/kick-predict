package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.TeamProfile

/** Room row caching one team's profile (team info & recent-form caching). */
@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val league: LeagueType,
    val name: String,
    val shortName: String,
    val leaguePosition: Int,
    val recentForm: List<MatchOutcome>,
    val overallRating: Double,
    val goalsScoredAvg: Double,
    val goalsConcededAvg: Double,
    val daysSinceLastMatch: Int,
) {
    fun toDomain(): TeamProfile = TeamProfile(
        id = id,
        name = name,
        shortName = shortName,
        leaguePosition = leaguePosition,
        recentForm = recentForm,
        overallRating = overallRating,
        goalsScoredAvg = goalsScoredAvg,
        goalsConcededAvg = goalsConcededAvg,
        daysSinceLastMatch = daysSinceLastMatch,
    )

    companion object {
        fun from(league: LeagueType, team: TeamProfile): TeamEntity = TeamEntity(
            id = team.id,
            league = league,
            name = team.name,
            shortName = team.shortName,
            leaguePosition = team.leaguePosition,
            recentForm = team.recentForm,
            overallRating = team.overallRating,
            goalsScoredAvg = team.goalsScoredAvg,
            goalsConcededAvg = team.goalsConcededAvg,
            daysSinceLastMatch = team.daysSinceLastMatch,
        )
    }
}
