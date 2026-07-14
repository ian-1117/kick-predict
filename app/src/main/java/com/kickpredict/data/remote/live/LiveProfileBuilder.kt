package com.kickpredict.data.remote.live

import com.kickpredict.data.TeamNamesKo
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.TeamProfile
import kotlin.math.abs
import kotlin.random.Random

/**
 * Shared helpers for turning a live standings row into a [TeamProfile]. Both live sources
 * (football-data.org, API-Football) expose the same handful of aggregates — position, games
 * played, goals for/against and a recent-form string — so the derivation is identical; only the
 * DTO shapes differ. Mirrors how [com.kickpredict.data.real.RealDataProvider] derives profiles
 * from CSVs so live and bundled fixtures look and predict alike.
 */
object LiveProfileBuilder {

    /** Aggregate season stats for one team, provider-agnostic. */
    data class TeamStats(
        val position: Int,
        val played: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val points: Int,
        val form: List<MatchOutcome>,
    )

    fun profile(id: String, name: String, shortName: String, stats: TeamStats?): TeamProfile {
        val (primary, secondary) = crest(id)
        val korean = TeamNamesKo.of(name).orEmpty()
        if (stats == null) {
            // No standings row yet (e.g. a just-promoted side before matchday 1): a neutral profile.
            return TeamProfile(
                id = id, name = name, shortName = shortName,
                leaguePosition = 0, recentForm = emptyList(),
                overallRating = 60.0, goalsScoredAvg = 1.3, goalsConcededAvg = 1.3,
                daysSinceLastMatch = 7, koreanName = korean, crestPrimary = primary, crestSecondary = secondary,
            )
        }
        val games = stats.played.coerceAtLeast(1)
        val ppg = if (stats.played == 0) 1.0 else stats.points.toDouble() / stats.played
        return TeamProfile(
            id = id,
            name = name,
            shortName = shortName,
            leaguePosition = stats.position,
            recentForm = stats.form,
            overallRating = round1(ratingFrom(ppg)),
            goalsScoredAvg = round1(stats.goalsFor.toDouble() / games),
            goalsConcededAvg = round1(stats.goalsAgainst.toDouble() / games),
            daysSinceLastMatch = 7,
            koreanName = korean,
            crestPrimary = primary,
            crestSecondary = secondary,
        )
    }

    fun shortCode(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return (if (letters.length >= 3) letters.take(3) else letters.padEnd(3, 'X')).uppercase()
    }

    /**
     * Parse a provider form string into a most-recent-first list of outcomes. Both providers emit
     * the most recent result *last* (e.g. "W,W,D,L,W" or "WWDLW"), so we keep the last five and
     * reverse. Non-letter separators are ignored.
     */
    fun parseForm(raw: String?): List<MatchOutcome> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.filter { it.isLetter() }
            .map {
                when (it.uppercaseChar()) {
                    'W' -> MatchOutcome.WIN
                    'D' -> MatchOutcome.DRAW
                    else -> MatchOutcome.LOSS
                }
            }
            .takeLast(5)
            .reversed()
    }

    private fun ratingFrom(pointsPerGame: Double): Double =
        (40.0 + pointsPerGame / 3.0 * 52.0).coerceIn(40.0, 95.0)

    private fun round1(v: Double) = (v * 10).toInt() / 10.0

    /** Deterministic vibrant crest colour from the team id (same scheme as the bundled provider). */
    private fun crest(id: String): Pair<Long, Long> {
        val hue = Random(id.hashCode()).nextInt(0, 360)
        return hsvToArgb(hue, 0.62, 0.72) to 0xFFFFFFFF
    }

    private fun hsvToArgb(h: Int, s: Double, v: Double): Long {
        val c = v * s
        val x = c * (1 - abs((h / 60.0) % 2 - 1))
        val m = v - c
        val (r, g, b) = when (h / 60) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        val ri = ((r + m) * 255).toLong(); val gi = ((g + m) * 255).toLong(); val bi = ((b + m) * 255).toLong()
        return 0xFF000000L or (ri shl 16) or (gi shl 8) or bi
    }
}
