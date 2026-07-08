package com.kickpredict.domain.model

/** Pitch/weather conditions and their effect on scoring. */
enum class Weather(val displayLabel: String, val goalFactor: Double) {
    CLEAR("맑음", 1.00),
    RAIN("비", 0.94),
    WIND("강풍", 0.92),
    SNOW("눈", 0.85),
    HEAT("폭염", 0.96);

    val isAdverse: Boolean get() = this != CLEAR
}

/**
 * A team's availability going into the fixture.
 *
 * @param keyPlayersInjured number of first-choice players unavailable.
 * @param lineupStrengthPercent starting XI strength vs a full-strength side (0..100).
 */
data class TeamAvailability(
    val keyPlayersInjured: Int = 0,
    val lineupStrengthPercent: Int = 100,
) {
    /** Multiplier applied to the team's expected goals; ~0.6..1.0. */
    val availabilityFactor: Double
        get() = (lineupStrengthPercent / 100.0 - 0.03 * keyPlayersInjured).coerceIn(0.6, 1.0)

    val isWeakened: Boolean get() = keyPlayersInjured > 0 || lineupStrengthPercent < 100
}

/**
 * Contextual variables for a fixture beyond raw team strength: injuries/lineup and weather.
 * Defaults describe a full-strength, fair-weather match (no effect).
 */
data class MatchContext(
    val homeAvailability: TeamAvailability = TeamAvailability(),
    val awayAvailability: TeamAvailability = TeamAvailability(),
    val weather: Weather = Weather.CLEAR,
) {
    /** True if any variable is in play — used to temper the confidence score. */
    val hasVariable: Boolean
        get() = homeAvailability.isWeakened || awayAvailability.isWeakened || weather.isAdverse
}
