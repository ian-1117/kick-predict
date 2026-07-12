package com.kickpredict.domain.model

/**
 * The score of a match that is currently in play. [minute] is the provider's status text (e.g. "45",
 * "90+2", "HT") shown next to a LIVE badge; blank when only the score is known.
 */
data class LiveScore(
    val home: Int,
    val away: Int,
    val minute: String = "",
) {
    val scoreline: String get() = "$home – $away"
}
