package com.kickpredict.domain.model

/**
 * A notify-worthy event about a match, produced by the scan and rendered into a localized system
 * notification by the Android notifier. Each carries a stable [key] so an event fires at most once.
 */
sealed interface MatchNotification {
    val matchId: String
    val key: String

    /** An upcoming pick is about to kick off (high-confidence or value). */
    data class Kickoff(
        override val matchId: String,
        val home: String,
        val away: String,
        val pickedOutcome: PredictedOutcome,
        val minutesToKickoff: Int,
        /** Non-null when the pick is also a value pick (edge over the market), in whole percent. */
        val edge: Int?,
    ) : MatchNotification {
        override val key: String get() = "kickoff:$matchId"
    }

    /** A match we're following just went live. */
    data class Live(
        override val matchId: String,
        val home: String,
        val away: String,
        val scoreline: String,
    ) : MatchNotification {
        override val key: String get() = "live:$matchId"
    }

    /** A predicted match finished — did the call land? */
    data class Result(
        override val matchId: String,
        val home: String,
        val away: String,
        val scoreline: String,
        val hit: Boolean,
        /** Value edge if this match was a flagged value pick, in whole percent — else null. */
        val edge: Int? = null,
    ) : MatchNotification {
        override val key: String get() = "result:$matchId"
    }
}
