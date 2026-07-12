package com.kickpredict.data.remote.live

import com.kickpredict.data.remote.apifootball.AfEvent
import com.kickpredict.data.remote.apifootball.ApiFootballApi
import com.kickpredict.data.remote.football.FootballDataApi
import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.LiveScore
import com.kickpredict.domain.model.MarketOdds
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

    // Final scores of the finished matches from the last successful live fetch, keyed by match id.
    // Seeded into the results store so standings/accuracy reflect the current season.
    @Volatile
    private var results: Map<String, Pair<Int, Int>> = emptyMap()
    fun lastResults(): Map<String, Pair<Int, Int>> = results

    // Scores of matches currently in play, keyed by match id (not seeded — they aren't final yet).
    @Volatile
    private var liveScores: Map<String, LiveScore> = emptyMap()
    fun lastLiveScores(): Map<String, LiveScore> = liveScores

    // Market 1X2 odds for upcoming matches, keyed by match id (K League only; the plan has no
    // European odds). Used to surface value picks where the model disagrees with the market.
    @Volatile
    private var odds: Map<String, MarketOdds> = emptyMap()
    fun lastOdds(): Map<String, MarketOdds> = odds

    /** One fetched fixture with its final score (if played) and its live score (if in play now). */
    private data class LiveFixture(val match: Match, val result: Pair<Int, Int>?, val live: LiveScore?)

    suspend fun getFixtures(): List<Match> {
        val today = clock()
        val europeSeason = europeanSeason(today)
        val kSeason = today.year
        val out = ArrayList<Match>()
        val resultsAcc = HashMap<String, Pair<Int, Int>>()
        val liveAcc = HashMap<String, LiveScore>()
        val oddsAcc = HashMap<String, MarketOdds>()

        europe.forEach { (code, league) ->
            val live = if (footballDataKey.isNotBlank()) {
                runCatching { europeFixtures(code, league, europeSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            out += collect(live, resultsAcc, liveAcc) ?: bundledByLeague(league)
        }
        kLeagues.forEach { (id, league) ->
            val live = if (apiFootballKey.isNotBlank()) {
                runCatching { kLeagueFixtures(id, league, kSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            out += collect(live, resultsAcc, liveAcc) ?: bundledByLeague(league)
            if (apiFootballKey.isNotBlank()) {
                runCatching { kLeagueOdds(id, today) }.getOrNull()?.let { oddsAcc.putAll(it) }
            }
        }
        results = resultsAcc
        liveScores = liveAcc
        odds = oddsAcc
        return out
    }

    /** Average 1X2 odds across bookmakers for the next few days, keyed by the app's match id. */
    private suspend fun kLeagueOdds(leagueId: Int, today: LocalDate): Map<String, MarketOdds> {
        val rows = apiFootball.odds(from = today.toString(), to = today.plusDays(4).toString(), leagueId = leagueId)
        return rows.groupBy { it.matchId }.mapNotNull { (matchId, group) ->
            val home = group.mapNotNull { it.home.toDoubleOrNull() }.averageOrNull() ?: return@mapNotNull null
            val draw = group.mapNotNull { it.draw.toDoubleOrNull() }.averageOrNull() ?: return@mapNotNull null
            val away = group.mapNotNull { it.away.toDoubleOrNull() }.averageOrNull() ?: return@mapNotNull null
            MarketOdds.of(home, draw, away)?.let { "AF_$matchId" to it }
        }.toMap()
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    /** Split fetched fixtures into the match list, harvesting final and live scores into the maps. */
    private fun collect(
        fixtures: List<LiveFixture>?,
        results: MutableMap<String, Pair<Int, Int>>,
        live: MutableMap<String, LiveScore>,
    ): List<Match>? {
        if (fixtures == null) return null
        fixtures.forEach { f ->
            if (f.result != null) results[f.match.id] = f.result
            if (f.live != null) live[f.match.id] = f.live
        }
        return fixtures.map { it.match }
    }

    // --- football-data.org (Europe) ---

    private suspend fun europeFixtures(code: String, league: LeagueType, season: Int): List<LiveFixture> {
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

        // Head-to-head from this season's finished matches (empty in preseason, fills in as it plays).
        val h2hRecords = matches.mapNotNull { m ->
            if (m.status != "FINISHED") return@mapNotNull null
            val h = m.score.fullTime.home ?: return@mapNotNull null
            val a = m.score.fullTime.away ?: return@mapNotNull null
            val d = runCatching { fromInstant(m.utcDate).toLocalDate() }.getOrNull() ?: return@mapNotNull null
            H2HRecord(teamKey(m.homeTeam.id, m.homeTeam.name), teamKey(m.awayTeam.id, m.awayTeam.name), h, a, d)
        }

        return matches.mapNotNull { m ->
            val homeName = m.homeTeam.name ?: return@mapNotNull null
            val awayName = m.awayTeam.name ?: return@mapNotNull null
            val homeKey = teamKey(m.homeTeam.id, homeName)
            val awayKey = teamKey(m.awayTeam.id, awayName)
            val homeId = "FD_${code}_$homeKey"
            val awayId = "FD_${code}_$awayKey"
            val match = Match(
                id = "FD_${code}_${m.id}",
                league = league,
                homeTeam = LiveProfileBuilder.profile(homeId, homeName, tlaOf(m.homeTeam.tla, homeName), stats[homeKey]),
                awayTeam = LiveProfileBuilder.profile(awayId, awayName, tlaOf(m.awayTeam.tla, awayName), stats[awayKey]),
                kickoff = fromInstant(m.utcDate),
                venue = homeName,
                headToHead = buildHeadToHead(homeKey, awayKey, h2hRecords),
                round = m.matchday ?: 1,
            )
            val h = m.score.fullTime.home
            val a = m.score.fullTime.away
            val live = if ((m.status == "IN_PLAY" || m.status == "PAUSED") && h != null && a != null) LiveScore(h, a) else null
            val score = if (m.status == "FINISHED" && h != null && a != null) h to a else null
            LiveFixture(match, score, live)
        }
    }

    // --- APIFootball (K League) ---

    private suspend fun kLeagueFixtures(leagueId: Int, league: LeagueType, season: Int): List<LiveFixture> {
        val standings = apiFootball.standings(leagueId = leagueId).associateBy { it.teamId }
        // Pull a few seasons in one call: the extra history only feeds head-to-head; fixtures, form
        // and results are still taken from the current season alone.
        val events = apiFootball.events(from = "${season - 2}-01-01", to = "$season-12-31", leagueId = leagueId)
        val h2hRecords = events.mapNotNull { e ->
            if (e.live == "1") return@mapNotNull null // in-play, not a settled result
            val d = parseDate(e.date) ?: return@mapNotNull null
            val hs = e.homeScore.toIntOrNull() ?: return@mapNotNull null
            val aws = e.awayScore.toIntOrNull() ?: return@mapNotNull null
            H2HRecord(e.homeId, e.awayId, hs, aws, d)
        }
        val current = events.filter { it.date.startsWith(season.toString()) }
        val form = recentForm(current)

        return current.mapNotNull { e ->
            val date = parseDate(e.date) ?: return@mapNotNull null
            val kickoff = LocalDateTime.of(date, parseTime(e.time) ?: LocalTime.of(15, 0))
            val homeStats = stats(standings[e.homeId]?.let { s -> s to form[e.homeId].orEmpty() })
            val awayStats = stats(standings[e.awayId]?.let { s -> s to form[e.awayId].orEmpty() })
            val match = Match(
                id = "AF_${e.id}",
                league = league,
                homeTeam = LiveProfileBuilder.profile("AF_${e.homeId}", e.homeName, LiveProfileBuilder.shortCode(e.homeName), homeStats),
                awayTeam = LiveProfileBuilder.profile("AF_${e.awayId}", e.awayName, LiveProfileBuilder.shortCode(e.awayName), awayStats),
                kickoff = kickoff,
                venue = e.stadium.ifBlank { e.homeName },
                headToHead = buildHeadToHead(e.homeId, e.awayId, h2hRecords),
                round = e.round.toIntOrNull() ?: 1,
            )
            val hs = e.homeScore.toIntOrNull()
            val aws = e.awayScore.toIntOrNull()
            val isLive = e.live == "1"
            val live = if (isLive && hs != null && aws != null) LiveScore(hs, aws, e.status) else null
            val score = if (!isLive && hs != null && aws != null) hs to aws else null
            LiveFixture(match, score, live)
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

    /** A played match, reduced to what head-to-head needs. */
    private data class H2HRecord(val homeId: String, val awayId: String, val homeGoals: Int, val awayGoals: Int, val date: LocalDate)

    /**
     * The recent meetings between two teams, most-recent-first and from the upcoming fixture's home
     * side's perspective — mirrors the bundled provider so live and bundled fixtures feed the 상성
     * (matchup) logic identically. [homeId]/[awayId] are the fixture's teams.
     */
    private fun buildHeadToHead(homeId: String, awayId: String, records: List<H2HRecord>): HeadToHead {
        val meetings = records
            .filter { (it.homeId == homeId && it.awayId == awayId) || (it.homeId == awayId && it.awayId == homeId) }
            .sortedByDescending { it.date }
            .take(8)
            .map { r ->
                val homeSideWon = r.homeGoals > r.awayGoals
                val draw = r.homeGoals == r.awayGoals
                val fixtureHomeWon = if (r.homeId == homeId) homeSideWon else (!homeSideWon && !draw)
                val outcome = when {
                    draw -> H2HOutcome.DRAW
                    fixtureHomeWon -> H2HOutcome.HOME_WIN
                    else -> H2HOutcome.AWAY_WIN
                }
                H2HMeeting(outcome = outcome, atHomeVenue = r.homeId == homeId)
            }
        return HeadToHead(meetings)
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
