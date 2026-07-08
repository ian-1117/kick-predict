package com.kickpredict.domain.model

import java.time.LocalDateTime

/**
 * A single fixture to be predicted. The [predictedResult] is populated by the
 * [com.kickpredict.domain.engine.PredictionEngine]; it is null until the engine has run.
 */
data class Match(
    val id: String,
    val league: LeagueType,
    val homeTeam: TeamProfile,
    val awayTeam: TeamProfile,
    val kickoff: LocalDateTime,
    val venue: String,
    val headToHead: HeadToHead,
    val round: Int = 1,
    val context: MatchContext = MatchContext(),
    val predictedResult: PredictionResult? = null,
)
