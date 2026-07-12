package com.kickpredict.domain.model

/** The three possible predicted outcomes of a fixture. */
enum class PredictedOutcome(val label: String) {
    HOME_WIN("Home Win"),
    DRAW("Draw"),
    AWAY_WIN("Away Win"),
}

/** Coarse buckets for the confidence score, used to colour/label the badge. */
enum class ConfidenceTier(val label: String) {
    VERY_HIGH("Very High"),
    HIGH("High"),
    MODERATE("Moderate"),
    LOW("Low"),
    VERY_LOW("Very Low"),
}

/** Which side the head-to-head matchup (상성) favours, and how strongly. */
enum class MatchupEdge { HOME, AWAY, NONE }

/**
 * Output of the prediction engine for one fixture.
 *
 * @param homeWinPercent / drawPercent / awayWinPercent probabilities in whole percent; sum to 100.
 * @param confidenceScore the headline "AI 예상 적중 확률" (0..100) — how much the engine trusts
 *        this particular prediction, derived from data volume, power gap, schedule variables and
 *        H2H consistency. This is distinct from the win probabilities above.
 * @param rationale short human-readable notes explaining the drivers, shown in the report panel.
 * @param matchupBias head-to-head 상성 signal in -1..1 (from the home team's view): positive means
 *        the home side historically dominates the fixture, negative means the away side is a bogey team.
 * @param expectedHomeGoals / expectedAwayGoals Poisson goal expectations (λ) for each side; also the
 *        basis for the most-likely scoreline shown in the UI.
 */
data class PredictionResult(
    val homeWinPercent: Int,
    val drawPercent: Int,
    val awayWinPercent: Int,
    val confidenceScore: Int,
    /**
     * The confidence the engine computed *before* the reliability curve was applied.
     *
     * This, not [confidenceScore], is what the curve must be fitted against: fitting on an already
     * calibrated score maps the correction back through itself, and the two values oscillate.
     * Display uses [confidenceScore]; calibration uses this.
     */
    val rawConfidenceScore: Int = confidenceScore,
    val rationale: List<String>,
    val matchupBias: Double = 0.0,
    val expectedHomeGoals: Double = 0.0,
    val expectedAwayGoals: Double = 0.0,
    /** P(total goals ≥ 3), i.e. Over 2.5, in whole percent. */
    val overProbabilityPercent: Int = 0,
    /** P(both teams score) in whole percent. */
    val bttsProbabilityPercent: Int = 0,
    /** Most-likely exact scorelines, highest probability first. */
    val topScorelines: List<ScoreLine> = emptyList(),
) {
    val underProbabilityPercent: Int get() = 100 - overProbabilityPercent
    val noBttsProbabilityPercent: Int get() = 100 - bttsProbabilityPercent

    /**
     * The scoreline shown as "expected". The most-likely exact scoreline that **agrees with the
     * predicted result** (so the headline score and the win/draw/loss call never contradict each
     * other), falling back to the rounded goal expectations if the top grid cells don't include one.
     */
    val expectedScoreline: String
        get() {
            val consistent = topScorelines.firstOrNull {
                when (predictedOutcome) {
                    PredictedOutcome.HOME_WIN -> it.homeGoals > it.awayGoals
                    PredictedOutcome.AWAY_WIN -> it.awayGoals > it.homeGoals
                    PredictedOutcome.DRAW -> it.homeGoals == it.awayGoals
                }
            }
            return consistent?.label
                ?: "${kotlin.math.round(expectedHomeGoals).toInt()} – ${kotlin.math.round(expectedAwayGoals).toInt()}"
        }

    val predictedOutcome: PredictedOutcome
        get() = when (maxOf(homeWinPercent, drawPercent, awayWinPercent)) {
            homeWinPercent -> PredictedOutcome.HOME_WIN
            awayWinPercent -> PredictedOutcome.AWAY_WIN
            else -> PredictedOutcome.DRAW
        }

    /** Which side the 상성 favours; NONE when the record is balanced or the sample is tiny. */
    val matchupEdge: MatchupEdge
        get() = when {
            matchupBias >= 0.15 -> MatchupEdge.HOME
            matchupBias <= -0.15 -> MatchupEdge.AWAY
            else -> MatchupEdge.NONE
        }

    /** Rough strength of the 상성 edge for labelling: "강한"/"뚜렷한" vs milder. 0..100. */
    val matchupStrengthPercent: Int
        get() = (kotlin.math.abs(matchupBias) * 100).toInt()

    val confidenceTier: ConfidenceTier
        get() = when {
            confidenceScore >= 80 -> ConfidenceTier.VERY_HIGH
            confidenceScore >= 65 -> ConfidenceTier.HIGH
            confidenceScore >= 50 -> ConfidenceTier.MODERATE
            confidenceScore >= 35 -> ConfidenceTier.LOW
            else -> ConfidenceTier.VERY_LOW
        }
}
