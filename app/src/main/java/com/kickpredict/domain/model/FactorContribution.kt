package com.kickpredict.domain.model

/** A driver in the engine's goal-ratio build, for the "what moved this prediction" breakdown. */
enum class FactorKind { BASE, QUALITY, RULES, MATCHUP, ELO, LEARNED, CONTEXT }

/**
 * How much one factor tilted the prediction, as a change in the log goal-ratio ln(λ_home / λ_away).
 * Because the engine builds λ multiplicatively, these deltas are additive and sum to the total tilt.
 * Positive leans toward the home side, negative toward the away side; the magnitude is the strength.
 */
data class FactorContribution(
    val kind: FactorKind,
    val tilt: Double,
)
