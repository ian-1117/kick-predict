package com.kickpredict.domain.model

/**
 * A single result from a team's point of view, used to describe recent form (W/D/L).
 * [points] follows the standard 3-1-0 football scoring so form can be scored numerically.
 */
enum class MatchOutcome(val shortLabel: String, val points: Int) {
    WIN("W", 3),
    DRAW("D", 1),
    LOSS("L", 0),
}
