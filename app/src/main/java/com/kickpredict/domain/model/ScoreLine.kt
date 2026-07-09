package com.kickpredict.domain.model

/** A specific final scoreline with its probability, from the Poisson score grid. */
data class ScoreLine(
    val homeGoals: Int,
    val awayGoals: Int,
    val probabilityPercent: Int,
) {
    val label: String get() = "$homeGoals – $awayGoals"
}
