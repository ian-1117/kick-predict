package com.kickpredict.domain.usecase

import com.kickpredict.domain.model.ConfidenceTier
import com.kickpredict.domain.model.LiveScore
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchNotification
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.PredictionResult
import com.kickpredict.domain.repository.CalibrationRepository
import java.time.ZoneId

/**
 * Scans the current fixtures + live state for notify-worthy events: a strong pick about to kick off,
 * a followed match going live, and a predicted match finishing (hit or miss). Returns every current
 * candidate; de-duping (fire-once) and posting are the caller's job.
 */
class GetPendingNotificationsUseCase(
    private val liveScores: () -> Map<String, LiveScore>,
    private val odds: () -> Map<String, MarketOdds>,
    private val calibrationRepository: CalibrationRepository,
    private val followedTeams: () -> Set<String> = { emptySet() },
    private val valueEdges: suspend () -> Map<String, Int> = { emptyMap() },
) {

    suspend operator fun invoke(matches: List<Match>, nowMillis: Long): List<MatchNotification> {
        val live = runCatching { liveScores() }.getOrDefault(emptyMap())
        val market = runCatching { odds() }.getOrDefault(emptyMap())
        val results = runCatching { calibrationRepository.recordedResults().associateBy { it.matchId } }
            .getOrDefault(emptyMap())
        val byId = matches.associateBy { it.id }
        // Once the user follows any team, only notify about matches involving a followed team.
        val followed = runCatching { followedTeams() }.getOrDefault(emptySet())
        // Value-pick edges keyed by match id, so a settled value pick's alert can be tagged.
        val edges = runCatching { valueEdges() }.getOrDefault(emptyMap())

        val out = mutableListOf<MatchNotification>()

        // Finished predictions → hit / miss (tagged with the value edge if it was a value pick).
        results.values.forEach { r ->
            if (followed.isNotEmpty() && r.homeTeamId !in followed && r.awayTeamId !in followed) return@forEach
            out += MatchNotification.Result(
                matchId = r.matchId,
                home = r.homeTeam,
                away = r.awayTeam,
                scoreline = r.scoreline,
                hit = r.wasCorrect,
                edge = edges[r.matchId],
            )
        }

        // Followed matches that just went live (and aren't already finished).
        live.forEach { (id, score) ->
            if (id in results) return@forEach
            val match = byId[id] ?: return@forEach
            if (followed.isNotEmpty() && match.homeTeam.id !in followed && match.awayTeam.id !in followed) return@forEach
            out += MatchNotification.Live(
                matchId = id,
                home = match.homeTeam.displayName,
                away = match.awayTeam.displayName,
                scoreline = score.scoreline,
            )
        }

        // Strong or value picks about to kick off.
        matches.forEach { match ->
            val prediction = match.predictedResult ?: return@forEach
            if (match.id in results || match.id in live) return@forEach
            if (followed.isNotEmpty() && match.homeTeam.id !in followed && match.awayTeam.id !in followed) return@forEach
            val kickoffMillis = match.kickoff.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val minutes = ((kickoffMillis - nowMillis) / 60_000L)
            if (minutes < 0 || minutes > KICKOFF_WINDOW_MINUTES) return@forEach
            val outcome = prediction.predictedOutcome
            val edge = market[match.id]?.let { modelPercent(prediction, outcome) - it.percentFor(outcome) }
            val isValue = edge != null && edge >= GetValuePicksUseCase.VALUE_EDGE_THRESHOLD
            val isStrong = prediction.confidenceTier == ConfidenceTier.HIGH ||
                prediction.confidenceTier == ConfidenceTier.VERY_HIGH
            if (!isValue && !isStrong) return@forEach
            out += MatchNotification.Kickoff(
                matchId = match.id,
                home = match.homeTeam.displayName,
                away = match.awayTeam.displayName,
                pickedOutcome = outcome,
                minutesToKickoff = minutes.toInt(),
                edge = if (isValue) edge else null,
            )
        }

        return out
    }

    companion object {
        /** Fire a kickoff notification once a strong pick is within this many minutes of kicking off. */
        const val KICKOFF_WINDOW_MINUTES = 60L

        private fun modelPercent(p: PredictionResult, outcome: PredictedOutcome): Int = when (outcome) {
            PredictedOutcome.HOME_WIN -> p.homeWinPercent
            PredictedOutcome.AWAY_WIN -> p.awayWinPercent
            PredictedOutcome.DRAW -> p.drawPercent
        }
    }
}
