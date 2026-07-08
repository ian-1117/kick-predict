package com.kickpredict.domain.model

/**
 * Everything the prediction engine needs to know about one team going into a fixture.
 *
 * @param recentForm most-recent-first list of up to five results (index 0 == last match).
 * @param leaguePosition 1-based table position (1 == top of the league).
 * @param overallRating scouting/market strength on a 0..100 scale.
 * @param goalsScoredAvg / goalsConcededAvg per-match averages this season.
 * @param daysSinceLastMatch used for the fixture-congestion (fatigue) rule.
 */
data class TeamProfile(
    val id: String,
    val name: String,
    val shortName: String,
    val leaguePosition: Int,
    val recentForm: List<MatchOutcome>,
    val overallRating: Double,
    val goalsScoredAvg: Double,
    val goalsConcededAvg: Double,
    val daysSinceLastMatch: Int,
    val koreanName: String = "",
    val crestPrimary: Long = 0xFF2E7D32,
    val crestSecondary: Long = 0xFFFFFFFF,
) {
    /** Korean display name when available, else the English name. */
    val displayName: String get() = koreanName.ifBlank { name }

    /** True when the team is playing on short rest (within 4 days of its previous game). */
    val isFatigued: Boolean get() = daysSinceLastMatch in 0..4

    /** Recent-form points expressed on a 0..1 scale; 0.5 when no form data exists yet. */
    val formScore: Double
        get() {
            val sample = recentForm.take(5)
            if (sample.isEmpty()) return 0.5
            val earned = sample.sumOf { it.points }
            val possible = sample.size * MatchOutcome.WIN.points
            return earned.toDouble() / possible
        }
}
