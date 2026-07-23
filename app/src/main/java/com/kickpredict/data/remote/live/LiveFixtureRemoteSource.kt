package com.kickpredict.data.remote.live

import com.kickpredict.data.remote.apisports.ApiSportsApi
import com.kickpredict.data.remote.apisports.AsStandingRow
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
 * (v3.football.api-sports.io). Each league is independent — if its key is missing or the call fails,
 * that league falls back to the bundled data via [bundledByLeague], so the app is never partially
 * empty and works with zero, one, or both keys configured.
 *
 * "Current season relative to now": European seasons are named by their starting year (Aug–May), so
 * from July onward we ask for this year's; the K League is a single calendar year.
 */
class LiveFixtureRemoteSource(
    private val footballData: FootballDataApi,
    private val apiSports: ApiSportsApi,
    private val footballDataKey: String,
    private val apiSportsKey: String,
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
        ApiSportsApi.K_LEAGUE_1 to LeagueType.K_LEAGUE,
        ApiSportsApi.K_LEAGUE_2 to LeagueType.K_LEAGUE_2,
    )

    /** True when at least one live source is configured; lets callers keep the bundled-only path. */
    val isConfigured: Boolean get() = footballDataKey.isNotBlank() || apiSportsKey.isNotBlank()

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

    /**
     * The fixtures plus whether the fetch was **degraded** — i.e. a league we hold a key for failed
     * (or came back empty) and had to be filled from bundled history. The repository uses this to
     * avoid overwriting a good cached season with a bundled fallback on a transient outage.
     */
    data class FetchResult(val matches: List<Match>, val degraded: Boolean)

    suspend fun getFixtures(): FetchResult {
        val today = clock()
        val europeSeason = europeanSeason(today)
        val kSeason = today.year
        val out = ArrayList<Match>()
        val resultsAcc = HashMap<String, Pair<Int, Int>>()
        val liveAcc = HashMap<String, LiveScore>()
        var degraded = false

        europe.forEach { (code, league) ->
            val live = if (footballDataKey.isNotBlank()) {
                runCatching { europeFixtures(code, league, europeSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            if (footballDataKey.isNotBlank() && live == null) degraded = true
            out += collect(live, resultsAcc, liveAcc) ?: bundledByLeague(league)
        }
        kLeagues.forEach { (id, league) ->
            val live = if (apiSportsKey.isNotBlank()) {
                runCatching { kLeagueFixtures(id, league, kSeason) }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else null
            if (apiSportsKey.isNotBlank() && live == null) degraded = true
            out += collect(live, resultsAcc, liveAcc) ?: bundledByLeague(league)
        }
        // Only publish fresh results/live on a clean fetch; on a degraded one keep the last good maps
        // so a transient outage doesn't wipe recorded results.
        if (!degraded) {
            results = resultsAcc
            liveScores = liveAcc
        }
        return FetchResult(out, degraded)
    }

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

    // --- API-Sports (K League) ---

    private val finishedStatuses = setOf("FT", "AET", "PEN")
    private val liveStatuses = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")

    private suspend fun kLeagueFixtures(leagueId: Int, league: LeagueType, season: Int): List<LiveFixture> {
        // One /standings call (table, form, scoring rates) + one /fixtures call (the season's games).
        val standings = apiSports.standings(leagueId, season)
            .response.firstOrNull()?.league?.standings?.flatten().orEmpty()
            .associateBy { it.team.id }
        val fixtures = apiSports.fixtures(leagueId, season).response

        // Head-to-head from this season's finished games (cheap; the engine tolerates a short history).
        val h2hRecords = fixtures.mapNotNull { f ->
            val hg = f.goals.home ?: return@mapNotNull null
            val ag = f.goals.away ?: return@mapNotNull null
            if (f.fixture.status.short !in finishedStatuses) return@mapNotNull null
            val d = runCatching { fromInstant(f.fixture.date).toLocalDate() }.getOrNull() ?: return@mapNotNull null
            H2HRecord(f.teams.home.id.toString(), f.teams.away.id.toString(), hg, ag, d)
        }

        return fixtures.mapNotNull { f ->
            val kickoff = runCatching { fromInstant(f.fixture.date) }.getOrNull() ?: return@mapNotNull null
            val homeStats = stats(standings[f.teams.home.id])
            val awayStats = stats(standings[f.teams.away.id])
            val match = Match(
                id = "AF_${f.fixture.id}",
                league = league,
                homeTeam = LiveProfileBuilder.profile("AF_${f.teams.home.id}", f.teams.home.name, LiveProfileBuilder.shortCode(f.teams.home.name), homeStats),
                awayTeam = LiveProfileBuilder.profile("AF_${f.teams.away.id}", f.teams.away.name, LiveProfileBuilder.shortCode(f.teams.away.name), awayStats),
                kickoff = kickoff,
                venue = f.fixture.venue.name?.ifBlank { null } ?: f.teams.home.name,
                headToHead = buildHeadToHead(f.teams.home.id.toString(), f.teams.away.id.toString(), h2hRecords),
                round = parseRound(f.league.round),
            )
            val short = f.fixture.status.short
            val hg = f.goals.home
            val ag = f.goals.away
            val live = if (short in liveStatuses && hg != null && ag != null) LiveScore(hg, ag, short) else null
            val score = if (short in finishedStatuses && hg != null && ag != null) hg to ag else null
            LiveFixture(match, score, live)
        }
    }

    private fun stats(row: AsStandingRow?): LiveProfileBuilder.TeamStats? {
        row ?: return null
        return LiveProfileBuilder.TeamStats(
            position = row.rank,
            played = row.all.played,
            goalsFor = row.all.goals.goalsFor,
            goalsAgainst = row.all.goals.goalsAgainst,
            points = row.points,
            form = LiveProfileBuilder.parseForm(row.form),
        )
    }

    /** Pull the round number out of API-Sports' "Regular Season - 12" round label. */
    private fun parseRound(round: String): Int =
        Regex("\\d+").find(round)?.value?.toIntOrNull() ?: 1

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

    // --- helpers ---

    private fun teamKey(id: Long?, name: String?): String = id?.toString() ?: (name ?: "").lowercase()

    private fun tlaOf(tla: String?, full: String): String = tla ?: LiveProfileBuilder.shortCode(full)

    /** Parse an ISO-8601 instant (with 'Z' or an offset) into local wall-clock time for display. */
    private fun fromInstant(iso: String): LocalDateTime =
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

    /** European season starting year: Aug–Dec belong to this year, Jan–Jun to the previous one. */
    private fun europeanSeason(today: LocalDate): Int =
        if (today.monthValue >= 7) today.year else today.year - 1
}
