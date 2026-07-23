package com.kickpredict.data.real

import android.content.res.AssetManager
import com.kickpredict.data.TeamColors
import com.kickpredict.data.TeamNamesKo
import com.kickpredict.domain.model.BacktestGame
import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.MarketOdds
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.PriorResult
import com.kickpredict.domain.model.TeamProfile
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * Loads **real** historical results bundled as CSV assets (football-data.co.uk) and turns them into
 * the app's domain objects:
 *  - [matches] — the latest season's fixtures with real teams (form, table position, scoring rates
 *    and head-to-head all derived from the data), for the engine to predict.
 *  - [priorResults] — the earlier seasons, used to pre-train the learned Elo / Dixon-Coles models so
 *    the latest-season predictions are genuinely out-of-sample.
 */
class RealDataProvider(private val openAsset: (String) -> InputStream) {

    constructor(assets: AssetManager) : this({ path -> assets.open(path) })

    private companion object {
        /** Pseudo-matches of prior-season evidence mixed into an in-season estimate; ~6 games of ballast. */
        const val PRIOR_WEIGHT = 6.0
    }

    private data class Row(val date: LocalDate, val time: LocalTime?, val home: String, val away: String, val hg: Int, val ag: Int)

    private val leagueCodes = linkedMapOf(
        "E0" to LeagueType.EPL,
        "SP1" to LeagueType.LALIGA,
        "I1" to LeagueType.SERIE_A,
        "D1" to LeagueType.BUNDESLIGA,
        // K League runs Feb–Nov in a single calendar year, so its files reuse the four shared season
        // slots as opaque keys: 2223→2022, 2324→2023, 2425→2024, 2526→2025 (the display season).
        "K1" to LeagueType.K_LEAGUE,
        "K2" to LeagueType.K_LEAGUE_2,
    )
    private val seasons = listOf("2223", "2324", "2425", "2526")
    private val displaySeason = "2526"

    // Display-name overrides keyed by canonical team name, for cases where the data's name is
    // ambiguous on its own. Everything else falls back to the raw name via TeamProfile.displayName.
    private val displayNameOverrides = mapOf(
        "Suwon" to "Suwon FC", // the data calls Suwon FC just "Suwon"; the Bluewings keep "Suwon Bluewings"
    )

    // code -> season -> rows
    private val data: Map<String, Map<String, List<Row>>> by lazy {
        leagueCodes.keys.associateWith { code -> seasons.associateWith { parse(code, it) } }
    }

    // Cheap check (no full parse): are the bundled assets present at all?
    val hasData: Boolean by lazy { runCatching { openAsset("realdata/E0_$displaySeason.csv").close(); true }.getOrDefault(false) }

    fun priorResults(): List<PriorResult> = leagueCodes.flatMap { (code, _) ->
        seasons.filter { it != displaySeason }.flatMap { season ->
            data.getValue(code).getValue(season).map { r ->
                PriorResult(teamId(code, r.home), teamId(code, r.away), r.hg, r.ag)
            }
        }
    }

    private val built: List<Triple<Match, Int, Int>> by lazy {
        leagueCodes.flatMap { (code, league) -> buildLeague(code, league) }
    }

    fun matches(): List<Match> = built.map { it.first }

    /** Real final scorelines for the display season, keyed by match id (for seeding standings/accuracy). */
    fun displayResults(): Map<String, Pair<Int, Int>> = built.associate { it.first.id to (it.second to it.third) }

    /**
     * Fixtures from [cutoffRound] onwards, with every team profile (table position, recent form,
     * scoring rates, rating) and head-to-head derived **only from matches played before that round**.
     *
     * [matches] builds its profiles from the whole display season, which is fine for a list of games
     * whose results are already known but would let a season projection peek at its own future. This
     * is the out-of-sample view: at the cutoff the model knows exactly what a spectator knew.
     *
     * Match ids and round numbers are identical to [matches], so results still join up.
     */
    fun matchesAsOf(cutoffRound: Int): List<Match> =
        leagueCodes.flatMap { (code, league) -> buildLeagueAsOf(code, league, cutoffRound) }

    private fun displayRows(code: String): List<Row> =
        data.getValue(code).getValue(displaySeason).sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MIDNIGHT }))

    private fun priorRows(code: String): List<Row> =
        seasons.filter { it != displaySeason }.flatMap { data.getValue(code).getValue(it) }

    private fun buildLeague(code: String, league: LeagueType): List<Triple<Match, Int, Int>> {
        val rows = displayRows(code)
        if (rows.isEmpty()) return emptyList()
        val allHistory = seasons.flatMap { data.getValue(code).getValue(it) }
        val teams = rows.flatMap { listOf(it.home, it.away) }.distinct()
        val stats = teams.associateWith { seasonStats(it, rows) }
        val ranked = teams.sortedWith(compareByDescending<String> { stats.getValue(it).points }.thenByDescending { stats.getValue(it).gd })
        val position = ranked.withIndex().associate { (i, name) -> name to i + 1 }
        val perRound = (teams.size / 2).coerceAtLeast(1)

        return rows.mapIndexed { index, r ->
            val round = index / perRound + 1
            val home = profile(code, league, r.home, stats.getValue(r.home), position.getValue(r.home))
            val away = profile(code, league, r.away, stats.getValue(r.away), position.getValue(r.away))
            val match = Match(
                id = matchId(code, r, round),
                league = league,
                homeTeam = home,
                awayTeam = away,
                kickoff = LocalDateTime.of(r.date, r.time ?: LocalTime.of(15, 0)),
                venue = r.home,
                headToHead = headToHead(r.home, r.away, allHistory),
                round = round,
            )
            Triple(match, r.hg, r.ag)
        }
    }

    private fun buildLeagueAsOf(code: String, league: LeagueType, cutoffRound: Int): List<Match> {
        val rows = displayRows(code)
        if (rows.isEmpty()) return emptyList()
        val teams = rows.flatMap { listOf(it.home, it.away) }.distinct()
        val perRound = (teams.size / 2).coerceAtLeast(1)
        // Rounds are assigned as index/perRound + 1, so the cutoff falls on a clean row boundary.
        val playedCount = ((cutoffRound - 1) * perRound).coerceIn(0, rows.size)
        val before = rows.subList(0, playedCount)
        val prior = priorRows(code)

        // Everything a spectator standing at the cutoff could know: earlier seasons plus this one so far.
        val history = prior + before
        val leagueGoalRate = goalRate(prior)
        val leaguePointRate = pointRate(prior)

        val current = teams.associateWith { seasonStats(it, before) }
        val form = teams.associateWith { recentForm(it, before, prior) }
        val shrunk = teams.associateWith { shrink(current.getValue(it), seasonStats(it, prior), leagueGoalRate, leaguePointRate) }

        // Early on, points separate almost nobody; fall back to prior-season strength so the table
        // isn't ordered by CSV row order at matchday 1.
        val ranked = teams.sortedWith(
            compareByDescending<String> { current.getValue(it).points }
                .thenByDescending { current.getValue(it).gd }
                .thenByDescending { shrunk.getValue(it).rating },
        )
        val position = ranked.withIndex().associate { (i, name) -> name to i + 1 }

        return rows.drop(playedCount).mapIndexed { offset, r ->
            val round = (playedCount + offset) / perRound + 1
            Match(
                id = matchId(code, r, round),
                league = league,
                homeTeam = asOfProfile(code, league, r.home, shrunk.getValue(r.home), form.getValue(r.home), position.getValue(r.home)),
                awayTeam = asOfProfile(code, league, r.away, shrunk.getValue(r.away), form.getValue(r.away), position.getValue(r.away)),
                kickoff = LocalDateTime.of(r.date, r.time ?: LocalTime.of(15, 0)),
                venue = r.home,
                headToHead = headToHead(r.home, r.away, history),
                round = round,
            )
        }
    }

    private fun matchId(code: String, r: Row, round: Int) =
        "${code}_${displaySeason}_${slug(r.home)}_${slug(r.away)}_r$round"

    /** A team's scoring/conceding/points rates once its thin early-season sample is pulled toward its prior seasons. */
    private data class Shrunk(val goalsFor: Double, val goalsAgainst: Double, val rating: Double)

    /**
     * Empirical-Bayes shrinkage: a team with no matches this season is described entirely by its prior
     * seasons (or, if it was just promoted and has none, by the league average); each match it plays
     * pulls the estimate toward what it is actually doing now.
     */
    private fun shrink(current: Stats, prior: Stats, leagueGoalRate: Double, leaguePointRate: Double): Shrunk {
        val priorGoalsFor = if (prior.played > 0) prior.gf.toDouble() / prior.played else leagueGoalRate
        val priorGoalsAgainst = if (prior.played > 0) prior.ga.toDouble() / prior.played else leagueGoalRate
        val priorPoints = if (prior.played > 0) prior.points.toDouble() / prior.played else leaguePointRate

        val weight = current.played + PRIOR_WEIGHT
        val goalsFor = (current.gf + PRIOR_WEIGHT * priorGoalsFor) / weight
        val goalsAgainst = (current.ga + PRIOR_WEIGHT * priorGoalsAgainst) / weight
        val pointsPerGame = (current.points + PRIOR_WEIGHT * priorPoints) / weight
        return Shrunk(goalsFor, goalsAgainst, ratingFrom(pointsPerGame))
    }

    /** Most recent five results before the cutoff, backfilled from last season when the season is young. */
    private fun recentForm(team: String, before: List<Row>, prior: List<Row>): List<MatchOutcome> {
        val thisSeason = seasonStats(team, before).form
        if (thisSeason.size >= 5) return thisSeason
        return (thisSeason + seasonStats(team, prior).form).take(5)
    }

    private fun goalRate(rows: List<Row>): Double =
        if (rows.isEmpty()) 1.35 else rows.sumOf { it.hg + it.ag }.toDouble() / (rows.size * 2)

    private fun pointRate(rows: List<Row>): Double {
        if (rows.isEmpty()) return 1.35
        val points = rows.sumOf { r -> if (r.hg == r.ag) 2 else 3 }
        return points.toDouble() / (rows.size * 2)
    }

    private fun ratingFrom(pointsPerGame: Double): Double =
        (40.0 + pointsPerGame / 3.0 * 52.0).coerceIn(40.0, 95.0)

    private fun asOfProfile(
        code: String,
        league: LeagueType,
        name: String,
        shrunk: Shrunk,
        form: List<MatchOutcome>,
        position: Int,
    ): TeamProfile {
        val (primary, secondary) = crest(teamId(code, name), name)
        return TeamProfile(
            id = teamId(code, name),
            name = name,
            shortName = shortCode(name),
            leaguePosition = position,
            recentForm = form,
            overallRating = (shrunk.rating * 10).toInt() / 10.0,
            goalsScoredAvg = (shrunk.goalsFor * 10).toInt() / 10.0,
            goalsConcededAvg = (shrunk.goalsAgainst * 10).toInt() / 10.0,
            daysSinceLastMatch = 7,
            koreanName = TeamNamesKo.of(name) ?: displayNameOverrides[name].orEmpty(),
            crestPrimary = primary,
            crestSecondary = secondary,
        )
    }

    private data class Stats(val played: Int, val gf: Int, val ga: Int, val points: Int, val form: List<MatchOutcome>) {
        val gd get() = gf - ga
    }

    private fun seasonStats(team: String, rows: List<Row>): Stats {
        var played = 0; var gf = 0; var ga = 0; var points = 0
        val form = ArrayList<MatchOutcome>()
        rows.forEach { r ->
            val isHome = r.home == team
            if (!isHome && r.away != team) return@forEach
            val (f, a) = if (isHome) r.hg to r.ag else r.ag to r.hg
            played++; gf += f; ga += a
            form += when { f > a -> MatchOutcome.WIN; f < a -> MatchOutcome.LOSS; else -> MatchOutcome.DRAW }
            points += when { f > a -> 3; f < a -> 0; else -> 1 }
        }
        return Stats(played, gf, ga, points, form.takeLast(5).reversed())
    }

    private fun profile(code: String, league: LeagueType, name: String, s: Stats, position: Int): TeamProfile {
        val ppg = if (s.played == 0) 1.0 else s.points.toDouble() / s.played
        val rating = ratingFrom(ppg)
        val (primary, secondary) = crest(teamId(code, name), name)
        val games = s.played.coerceAtLeast(1)
        return TeamProfile(
            id = teamId(code, name),
            name = name,
            shortName = shortCode(name),
            leaguePosition = position,
            recentForm = s.form,
            overallRating = (rating * 10).toInt() / 10.0,
            goalsScoredAvg = (s.gf.toDouble() / games * 10).toInt() / 10.0,
            goalsConcededAvg = (s.ga.toDouble() / games * 10).toInt() / 10.0,
            daysSinceLastMatch = 7,
            koreanName = TeamNamesKo.of(name) ?: displayNameOverrides[name].orEmpty(),
            crestPrimary = primary,
            crestSecondary = secondary,
        )
    }

    /** Prior meetings between the two clubs, most-recent-first, from the home team's perspective. */
    private fun headToHead(home: String, away: String, history: List<Row>): HeadToHead {
        val meetings = history
            .filter { (it.home == home && it.away == away) || (it.home == away && it.away == home) }
            .sortedByDescending { it.date }
            .take(8)
            .map { r ->
                val homeWon = r.hg > r.ag
                val draw = r.hg == r.ag
                val homeTeamWon = if (r.home == home) homeWon else (!homeWon && !draw)
                val outcome = when {
                    draw -> H2HOutcome.DRAW
                    homeTeamWon -> H2HOutcome.HOME_WIN
                    else -> H2HOutcome.AWAY_WIN
                }
                H2HMeeting(outcome = outcome, atHomeVenue = r.home == home)
            }
        return HeadToHead(meetings)
    }

    /**
     * Every bundled historical match with its result and (where the CSV carries them) average +
     * closing 1X2 odds — the input to the walk-forward backtest. Odds-less leagues (K League) still
     * contribute to accuracy/proper scores, just not to ROI/CLV.
     */
    fun backtestGames(): List<BacktestGame> = leagueCodes.flatMap { (code, league) ->
        seasons.flatMap { season -> parseBacktest(code, season, league) }
    }

    private fun parseBacktest(code: String, season: String, league: LeagueType): List<BacktestGame> {
        val text = runCatching { openAsset("realdata/${code}_$season.csv").bufferedReader().use { it.readText() } }.getOrNull() ?: return emptyList()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()
        val h = lines.first().split(",")
        val di = h.indexOf("Date"); val hi = h.indexOf("HomeTeam"); val ai = h.indexOf("AwayTeam")
        val hgi = h.indexOf("FTHG"); val agi = h.indexOf("FTAG")
        if (listOf(di, hi, ai, hgi, agi).any { it < 0 }) return emptyList()
        val avgH = h.indexOf("AvgH"); val avgD = h.indexOf("AvgD"); val avgA = h.indexOf("AvgA")
        val avgCH = h.indexOf("AvgCH"); val avgCD = h.indexOf("AvgCD"); val avgCA = h.indexOf("AvgCA")
        return lines.drop(1).mapNotNull { line ->
            val f = line.split(",")
            if (maxOf(di, hi, ai, hgi, agi) >= f.size) return@mapNotNull null
            val date = parseDate(f[di]) ?: return@mapNotNull null
            val hg = f[hgi].toIntOrNull() ?: return@mapNotNull null
            val ag = f[agi].toIntOrNull() ?: return@mapNotNull null
            val home = f[hi].trim(); val away = f[ai].trim()
            if (home.isEmpty() || away.isEmpty()) return@mapNotNull null
            BacktestGame(
                league = league,
                date = date,
                homeId = teamId(code, home),
                awayId = teamId(code, away),
                homeGoals = hg,
                awayGoals = ag,
                avgOdds = oddsAt(f, avgH, avgD, avgA),
                closeOdds = oddsAt(f, avgCH, avgCD, avgCA),
            )
        }
    }

    private fun oddsAt(f: List<String>, hi: Int, di: Int, ai: Int): MarketOdds? {
        if (hi < 0 || di < 0 || ai < 0 || maxOf(hi, di, ai) >= f.size) return null
        val home = f[hi].toDoubleOrNull() ?: return null
        val draw = f[di].toDoubleOrNull() ?: return null
        val away = f[ai].toDoubleOrNull() ?: return null
        return MarketOdds.of(home, draw, away)
    }

    private fun parse(code: String, season: String): List<Row> {
        val text = runCatching { openAsset("realdata/${code}_$season.csv").bufferedReader().use { it.readText() } }.getOrNull() ?: return emptyList()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()
        val h = lines.first().split(",")
        val di = h.indexOf("Date"); val ti = h.indexOf("Time")
        val hi = h.indexOf("HomeTeam"); val ai = h.indexOf("AwayTeam")
        val hgi = h.indexOf("FTHG"); val agi = h.indexOf("FTAG")
        if (listOf(di, hi, ai, hgi, agi).any { it < 0 }) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = line.split(",")
            if (maxOf(di, hi, ai, hgi, agi) >= f.size) return@mapNotNull null
            val date = parseDate(f[di]) ?: return@mapNotNull null
            val time = if (ti in 0 until f.size) parseTime(f[ti]) else null
            val hg = f[hgi].toIntOrNull() ?: return@mapNotNull null
            val ag = f[agi].toIntOrNull() ?: return@mapNotNull null
            val home = f[hi].trim(); val away = f[ai].trim()
            if (home.isEmpty() || away.isEmpty()) null else Row(date, time, home, away, hg, ag)
        }
    }

    private fun parseDate(s: String): LocalDate? {
        val p = s.trim().split("/")
        if (p.size != 3) return null
        val day = p[0].toIntOrNull() ?: return null
        val month = p[1].toIntOrNull() ?: return null
        var year = p[2].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseTime(s: String): LocalTime? {
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }

    private fun teamId(code: String, name: String) = "${code}_${slug(name)}"
    private fun slug(name: String) = name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    private fun shortCode(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return (if (letters.length >= 3) letters.take(3) else letters.padEnd(3, 'X')).uppercase()
    }

    /** The club's real brand colour (text derived from luminance) when known, else a hashed colour. */
    private fun crest(id: String, name: String): Pair<Long, Long> {
        TeamColors.of(name)?.let { return it to TeamColors.foregroundFor(it) }
        val hue = Random(id.hashCode()).nextInt(0, 360)
        return hsvToArgb(hue, 0.62, 0.72) to 0xFFFFFFFF
    }

    private fun hsvToArgb(h: Int, s: Double, v: Double): Long {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60.0) % 2 - 1))
        val m = v - c
        val (r, g, b) = when (h / 60) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        val ri = ((r + m) * 255).toLong(); val gi = ((g + m) * 255).toLong(); val bi = ((b + m) * 255).toLong()
        return 0xFF000000L or (ri shl 16) or (gi shl 8) or bi
    }
}
