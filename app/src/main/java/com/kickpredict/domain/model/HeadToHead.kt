package com.kickpredict.domain.model

import kotlin.math.pow

/** A single past meeting's result, from the home team's perspective. */
enum class H2HOutcome(val shortLabel: String) {
    HOME_WIN("W"),
    DRAW("D"),
    AWAY_WIN("L"),
}

/**
 * One past meeting between the two teams.
 *
 * @param outcome result from the current home team's perspective.
 * @param atHomeVenue true if that meeting was played at the *current fixture's* home ground — i.e.
 *        the same venue as the upcoming match. Same-venue meetings are more predictive, so the
 *        matchup weighting counts them more heavily (홈/원정 구분).
 */
data class H2HMeeting(
    val outcome: H2HOutcome,
    val atHomeVenue: Boolean,
)

/**
 * Head-to-head history between the two teams, stored as an ordered list of past meetings —
 * **most recent first** — from the home team's perspective. Feeds both the base prediction
 * (venue- and recency-weighted matchup/상성) and the confidence score (record consistency).
 */
data class HeadToHead(
    val recentMeetings: List<H2HMeeting> = emptyList(),
) {
    val homeWins: Int get() = recentMeetings.count { it.outcome == H2HOutcome.HOME_WIN }
    val draws: Int get() = recentMeetings.count { it.outcome == H2HOutcome.DRAW }
    val awayWins: Int get() = recentMeetings.count { it.outcome == H2HOutcome.AWAY_WIN }
    val total: Int get() = recentMeetings.size

    /** Record restricted to meetings played at the upcoming fixture's home venue. */
    val homeVenueRecord: Triple<Int, Int, Int>
        get() {
            val atVenue = recentMeetings.filter { it.atHomeVenue }
            return Triple(
                atVenue.count { it.outcome == H2HOutcome.HOME_WIN },
                atVenue.count { it.outcome == H2HOutcome.DRAW },
                atVenue.count { it.outcome == H2HOutcome.AWAY_WIN },
            )
        }

    /**
     * How lopsided the record is, on a 0..1 scale.
     * 0 == perfectly mixed (no reliable signal), 1 == one outcome always happens.
     */
    val consistency: Double
        get() {
            if (total == 0) return 0.0
            val dominant = maxOf(homeWins, draws, awayWins).toDouble() / total
            return ((dominant - (1.0 / 3.0)) / (1.0 - (1.0 / 3.0))).coerceIn(0.0, 1.0)
        }

    /**
     * Venue- and recency-weighted matchup bias in -1..1 (home perspective): +1 the home side has
     * been winning, -1 the away side. The i-th most-recent meeting is weighted `decay^i`, and a
     * meeting played at this fixture's venue is additionally scaled by [venueBoost].
     */
    fun weightedBias(decay: Double, venueBoost: Double): Double {
        if (recentMeetings.isEmpty()) return 0.0
        var numerator = 0.0
        var denominator = 0.0
        recentMeetings.forEachIndexed { index, meeting ->
            val weight = decay.pow(index) * (if (meeting.atHomeVenue) venueBoost else 1.0)
            numerator += when (meeting.outcome) {
                H2HOutcome.HOME_WIN -> 1.0
                H2HOutcome.AWAY_WIN -> -1.0
                H2HOutcome.DRAW -> 0.0
            } * weight
            denominator += weight
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    companion object {
        /**
         * Build from aggregate counts when neither order nor venue is known (degenerates toward a
         * plain tally). Prefer the primary constructor with a real most-recent-first list.
         */
        fun of(homeWins: Int, draws: Int, awayWins: Int): HeadToHead = HeadToHead(
            List(homeWins) { H2HMeeting(H2HOutcome.HOME_WIN, atHomeVenue = false) } +
                List(draws) { H2HMeeting(H2HOutcome.DRAW, atHomeVenue = false) } +
                List(awayWins) { H2HMeeting(H2HOutcome.AWAY_WIN, atHomeVenue = false) },
        )
    }
}
