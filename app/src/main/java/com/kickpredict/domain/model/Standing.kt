package com.kickpredict.domain.model

/** One row of a league table, computed from recorded results. */
data class Standing(
    val teamId: String,
    val teamName: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    val points: Int get() = won * 3 + drawn
    val goalDiff: Int get() = goalsFor - goalsAgainst
}
