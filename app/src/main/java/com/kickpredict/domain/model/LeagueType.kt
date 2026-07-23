package com.kickpredict.domain.model

/**
 * The leagues supported by Kick Predict. [displayName] is used directly in the UI.
 * Each league carries its own quirks that the [com.kickpredict.domain.engine.PredictionEngine]
 * folds in as league-specific weightings.
 */
enum class LeagueType(val displayName: String, val country: String) {
    EPL("Premier League", "England"),
    LALIGA("LaLiga", "Spain"),
    SERIE_A("Serie A", "Italy"),
    BUNDESLIGA("Bundesliga", "Germany"),
    K_LEAGUE("K League 1", "Korea"),
    K_LEAGUE_2("K League 2", "Korea"),
}
