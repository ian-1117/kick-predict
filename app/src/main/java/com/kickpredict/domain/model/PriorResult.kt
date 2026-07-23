package com.kickpredict.domain.model

/** A historical result used to pre-train the learned models (Elo / Dixon-Coles) before the app runs. */
data class PriorResult(
    val homeTeamId: String,
    val awayTeamId: String,
    val homeGoals: Int,
    val awayGoals: Int,
)
