package com.kickpredict.data.remote.live

import com.kickpredict.data.remote.apifootball.AfEvent
import com.kickpredict.data.remote.apifootball.ApiFootballApi
import com.kickpredict.data.remote.football.FootballDataApi
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchOutcome
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Fetches a full **current** season of fixtures live and turns them into domain [Match]es the
 * engine can predict — the "predict what's coming" path, as opposed to the bundled historical
 * seasons used for backtesting.
 *
 * League-level hybrid: Europe comes from football-data.org, the two K League tiers from APIFootball
 * (apiv3.apifootball.com). Each league is independent — if its key is missing or the call fails,
 * that league falls back to the bundled data via [bundledByLeague], so the app is never partially
 * empty and works with zero, one, or both keys configured.
 *
 * "Current season relative to now": European seasons are named by their starting year (Aug–May), so
 * from July onward we ask for this year's; the K League is a single calendar year.
 */
class LiveFixtureRemoteSource(
    private val footballData: FootballDataApi,
    private val apiFootball: ApiFootballApi,
    private val footballDataKey: String,
    private val apiFootballKey: String,
    private val bundledByLeague: (LeagueType) -> List<Match>,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {

    private val europe = listOf(
        "PL" to LeagueType.EPL,
        "PD" to LeagueType.LALIGA,
        "SA" to LeagueType.SERIE_A,
        "BL1" to LeagueType.BUNDESLIGA,
    )
    private val kLeagues = listOf(
        ApiFootballApi.K_LEAGUE_1 to LeagueType.K_LEAGUE,
        ApiFootballApi.K_LEAGUE_2 to LeagueType.K_LEAGUE_2,
    )

    /** True when at least one live source is configured; lets callers keep the bundled-only path. */
    val isConfigured: Boolean get() = footballDataKey.isNotBlank() || apiFootballKey.isNotBlank()

    suspend fun getFixtures(): List<Match> {
        val today = clock()
        val europeSeason = europeanSeason(today)
        val kSeason = today.year
        val out = ArrayList<Match>()

        europe.forEach { (code, league) ->
            val live = if (footballDataKey.isNotBlank()) {
                runCatching { europeFixtures(code, league, europeSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            out += live ?: bundledByLeague(league)
        }
        kLeagues.forEach { (id, league) ->
            val live = if (apiFootballKey.isNotBlank()) {
                runCatching { kLeagueFixtures(id, league, kSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            out += live ?: bundledByLeague(league)
        }
        return out
    }

    // --- football-data.org (Europe) ---

    private suspend fun europeFixtures(code: String, league: LeagueType, season: Int): List<Match> {
        val matches = footballData.matches(code, season).matches
        if (matches.isEmpty()) return emptyList()

        // Before the season kicks off its standings are empty, so every team would look identical.
        // Fall back to last season's final table for strength — team ids are stable across seasons,
        // so the join still lands. Standings are best-effort: on failure fixtures still show live.
        val hasResults = matches.any { it.status == "FINISHED" }
        val standingsSeason = if (hasResults) season else season - 1
        val stats = runCatching {
            footballData.standings(code, standingsSeason).standings
                .firstOrNull { it.type == "TOTAL" }?.table.orEmpty()
                .associate { row ->
                    teamKey(row.team.id, row.team.name) to LiveProfileBuilder.TeamStats(
                        position = row.position,
                        played = row.playedGames,
                        goalsFor = row.goalsFor,
                        goalsAgainst = row.goalsAgainst,
                        points = row.points,
                        form = LiveProfileBuilder.parseForm(row.form),
                    )
                }
        }.getOrDefault(emptyMap())

        return matches.mapNotNull { m ->
            val homeName = m.homeTeam.name ?: return@mapNotNull null
            val awayName = m.awayTeam.name ?: return@mapNotNull null
            val homeId = "FD_${code}_${teamKey(m.homeTeam.id, homeName)}"
            val awayId = "FD_${code}_${teamKey(m.awayTeam.id, awayName)}"
            Match(
                id = "FD_${code}_${m.id}",
                league = league,
                homeTeam = LiveProfileBuilder.profile(homeId, homeName, tlaOf(m.homeTeam.tla, homeName), stats[teamKey(m.homeTeam.id, homeName)]),
                awayTeam = LiveProfileBuilder.profile(awayId, awayName, tlaOf(m.awayTeam.tla, awayName), stats[teamKey(m.awayTeam.id, awayName)]),
                kickoff = fromInstant(m.utcDate),
                venue = homeName,
                headToHead = HeadToHead(),
                round = m.matchday ?: 1,
            )
        }
    }

    // --- APIFootball (K League) ---

    private suspend fun kLeagueFixtures(leagueId: Int, league: LeagueType, season: Int): List<Match> {
        val standings = apiFootball.standings(leagueId = leagueId).associateBy { it.teamId }
        val events = apiFootball.events(from = "$season-01-01", to = "$season-12-31", leagueId = leagueId)
        val form = recentForm(events)

        return events.mapNotNull { e ->
            val date = parseDate(e.date) ?: return@mapNotNull null
            val kickoff = LocalDateTime.of(date, parseTime(e.time) ?: LocalTime.of(15, 0))
            val homeStats = stats(standings[e.homeId]?.let { s -> s to form[e.homeId].orEmpty() })
            val awayStats = stats(standings[e.awayId]?.let { s -> s to form[e.awayId].orEmpty() })
            Match(
                id = "AF_${e.id}",
                league = league,
                homeTeam = LiveProfileBuilder.profile("AF_${e.homeId}", e.homeName, LiveProfileBuilder.shortCode(e.homeName), homeStats),
                awayTeam = LiveProfileBuilder.profile("AF_${e.awayId}", e.awayName, LiveProfileBuilder.shortCode(e.awayName), awayStats),
                kickoff = kickoff,
                venue = e.stadium.ifBlank { e.homeName },
                headToHead = HeadToHead(),
                round = e.round.toIntOrNull() ?: 1,
            )
        }
    }

    private fun stats(pair: Pair<com.kickpredict.data.remote.apifootball.AfStanding, List<MatchOutcome>>?): LiveProfileBuilder.TeamStats? {
        val (s, form) = pair ?: return null
        return LiveProfileBuilder.TeamStats(
            position = s.position.toIntOrNull() ?: 0,
            played = s.played.toIntOrNull() ?: 0,
            goalsFor = s.goalsFor.toIntOrNull() ?: 0,
            goalsAgainst = s.goalsAgainst.toIntOrNull() ?: 0,
            points = s.points.toIntOrNull() ?: 0,
            form = form,
        )
    }

    /** Per-team most-recent-first form from the finished events (blank scores == not played yet). */
    private fun recentForm(events: List<AfEvent>): Map<String, List<MatchOutcome>> {
        val byTeam = HashMap<String, MutableList<MatchOutcome>>()
        events.asSequence()
            .filter { it.homeScore.isNotBlank() && it.awayScore.isNotBlank() }
            .sortedBy { it.date } // ISO dates sort chronologically as plain strings
            .forEach { e ->
                val hs = e.homeScore.toIntOrNull() ?: return@forEach
                val aws = e.awayScore.toIntOrNull() ?: return@forEach
                byTeam.getOrPut(e.homeId) { ArrayList() }.add(outcome(hs, aws))
                byTeam.getOrPut(e.awayId) { ArrayList() }.add(outcome(aws, hs))
            }
        return byTeam.mapValues { it.value.takeLast(5).reversed() }
    }

    private fun outcome(scored: Int, conceded: Int): MatchOutcome = when {
        scored > conceded -> MatchOutcome.WIN
        scored < conceded -> MatchOutcome.LOSS
        else -> MatchOutcome.DRAW
    }

    // --- helpers ---

    private fun teamKey(id: Long?, name: String?): String = id?.toString() ?: (name ?: "").lowercase()

    private fun tlaOf(tla: String?, full: String): String = tla ?: LiveProfileBuilder.shortCode(full)

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    private fun parseTime(s: String): LocalTime? {
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }

    /** Parse an ISO-8601 instant (with 'Z' or an offset) into local wall-clock time for display. */
    private fun fromInstant(iso: String): LocalDateTime =
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

    /** European season starting year: Aug–Dec belong to this year, Jan–Jun to the previous one. */
    private fun europeanSeason(today: LocalDate): Int =
        if (today.monthValue >= 7) today.year else today.year - 1
}
