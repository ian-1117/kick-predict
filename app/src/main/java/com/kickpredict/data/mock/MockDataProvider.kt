package com.kickpredict.data.mock

import com.kickpredict.domain.model.H2HMeeting
import com.kickpredict.domain.model.H2HOutcome
import com.kickpredict.domain.model.HeadToHead
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.model.MatchContext
import com.kickpredict.domain.model.MatchOutcome
import com.kickpredict.domain.model.TeamAvailability
import com.kickpredict.domain.model.TeamProfile
import com.kickpredict.domain.model.Weather
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Sample fixtures for the app: the four hand-tuned product scenarios (A–D, real clubs but the exact
 * stats the engine tests rely on) plus a deterministically generated multi-round schedule across all
 * five leagues, so league / round / week / date-range filtering has real data to work with.
 */
object MockDataProvider {

    private val seasonStart: LocalDate = LocalDate.of(2026, 7, 11) // round 1 weekend (Saturday)

    fun matches(): List<Match> = scenarios() + generatedSchedule()

    // ---- The four tuned scenarios (identities swapped to real clubs; stats unchanged) ----------

    private fun scenarios(): List<Match> = listOf(
        // A — EPL: runaway leader vs bottom club -> dominant home win, very-high confidence.
        Match(
            id = "match_a", league = LeagueType.EPL, round = 1,
            homeTeam = p("epl_mci", "Manchester City", "맨체스터 시티", "MCI", 0xFF6CABDD, 0xFF1C2C5B,
                pos = 1, rating = 90.0, form = "WWWWW", gs = 2.6, gc = 0.6, rest = 7),
            awayTeam = p("epl_shu", "Sheffield United", "셰필드 유나이티드", "SHU", 0xFFEE2737, 0xFFFFFFFF,
                pos = 20, rating = 62.0, form = "LLLLL", gs = 0.8, gc = 2.4, rest = 6),
            kickoff = LocalDateTime.of(2026, 7, 11, 16, 30), venue = "Etihad Stadium",
            headToHead = HeadToHead(listOf(won(false), won(true), drew(false), won(true), won(false))),
        ),
        // B — Serie A: two even, draw-prone sides -> draw favoured, moderate confidence.
        Match(
            id = "match_b", league = LeagueType.SERIE_A, round = 1,
            homeTeam = p("ita_ver", "Hellas Verona", "엘라스 베로나", "VER", 0xFF1E2952, 0xFFFFD200,
                pos = 10, rating = 73.0, form = "DD", gs = 1.0, gc = 1.0, rest = 5),
            awayTeam = p("ita_gen", "Genoa", "제노아", "GEN", 0xFFB01722, 0xFF12284B,
                pos = 9, rating = 73.0, form = "DD", gs = 1.0, gc = 1.0, rest = 6),
            kickoff = LocalDateTime.of(2026, 7, 12, 20, 45), venue = "Stadio Marc'Antonio Bentegodi",
            headToHead = HeadToHead(listOf(drew(false), drew(true), drew(false), drew(true))),
        ),
        // C — K League: rested home vs midweek-tired, injured away in the rain -> low confidence.
        Match(
            id = "match_c", league = LeagueType.K_LEAGUE, round = 1,
            homeTeam = p("kor_uls", "Ulsan HD", "울산 HD", "ULS", 0xFF002D62, 0xFFF7A800,
                pos = 6, rating = 75.0, form = "WDWL", gs = 1.4, gc = 1.25, rest = 7),
            awayTeam = p("kor_jbk", "Jeonbuk Hyundai", "전북 현대", "JBK", 0xFF00623C, 0xFFFFFFFF,
                pos = 4, rating = 77.0, form = "WWDL", gs = 1.55, gc = 1.2, rest = 3),
            kickoff = LocalDateTime.of(2026, 7, 11, 19, 0), venue = "Ulsan Munsu Football Stadium",
            headToHead = HeadToHead(listOf(lost(false), won(true), drew(false), lost(true), won(false))),
            context = MatchContext(
                awayAvailability = TeamAvailability(keyPlayersInjured = 1, lineupStrengthPercent = 90),
                weather = Weather.RAIN,
            ),
        ),
        // D — EPL: mid-table bogey home vs a title-chasing, injury-hit visitor -> 상성 swings it.
        Match(
            id = "match_d", league = LeagueType.EPL, round = 1,
            homeTeam = p("epl_cry", "Crystal Palace", "크리스탈 팰리스", "CRY", 0xFF1B458F, 0xFFC4122E,
                pos = 14, rating = 70.0, form = "WDLDL", gs = 1.1, gc = 1.4, rest = 7),
            awayTeam = p("epl_ars", "Arsenal", "아스날", "ARS", 0xFFEF0107, 0xFFFFFFFF,
                pos = 2, rating = 85.0, form = "WWWDW", gs = 2.2, gc = 0.9, rest = 6),
            kickoff = LocalDateTime.of(2026, 7, 12, 17, 30), venue = "Selhurst Park",
            headToHead = HeadToHead(listOf(won(true), won(false), won(true), won(true), drew(false), won(true))),
            context = MatchContext(
                awayAvailability = TeamAvailability(keyPlayersInjured = 2, lineupStrengthPercent = 85),
            ),
        ),
    )

    // ---- Deterministic multi-round schedule -----------------------------------------------------

    private const val ROUNDS = 5

    private fun generatedSchedule(): List<Match> = buildList {
        Teams.byLeague.forEach { (league, seeds) ->
            addAll(leagueSchedule(league, ROUNDS))
        }
    }

    /** Generates [rounds] rounds of fixtures for a single [league] (used for focused simulations). */
    internal fun leagueSchedule(league: LeagueType, rounds: Int): List<Match> {
        val seeds = Teams.byLeague.getValue(league)
        return roundRobin(seeds.size, rounds).flatMapIndexed { roundIndex, pairs ->
            pairs.mapIndexed { matchIndex, (h, a) ->
                fixture(league, seeds[h], seeds[a], roundIndex + 1, matchIndex)
            }
        }
    }

    private fun fixture(
        league: LeagueType,
        home: TeamSeed,
        away: TeamSeed,
        round: Int,
        indexInRound: Int,
    ): Match {
        val weekend = seasonStart.plusWeeks((round - 1).toLong())
        val onSunday = indexInRound % 2 == 1
        val day = if (onSunday) weekend.plusDays(1) else weekend
        val kickoff = day.atTime(15 + (indexInRound / 2) * 2, if (indexInRound % 2 == 0) 0 else 30)

        val homeSeedIdx = Teams.byLeague.getValue(league).indexOfFirst { it.id == home.id }
        val awaySeedIdx = Teams.byLeague.getValue(league).indexOfFirst { it.id == away.id }
        return Match(
            id = "${league.name.lowercase()}_r${round}_${home.short}_${away.short}",
            league = league,
            round = round,
            homeTeam = profileFromSeed(home, homeSeedIdx + 1),
            awayTeam = profileFromSeed(away, awaySeedIdx + 1),
            kickoff = kickoff,
            venue = "${home.name} Stadium",
            headToHead = genH2H(home, away),
        )
    }

    /** Stable per-team profile derived from its rating (form/goals seeded by team id). */
    private fun profileFromSeed(seed: TeamSeed, position: Int): TeamProfile {
        val rng = Random(seed.id.hashCode())
        val pWin = ((seed.rating - 65) / 30.0).coerceIn(0.15, 0.75)
        val form = List(5) {
            val r = rng.nextDouble()
            when {
                r < pWin -> MatchOutcome.WIN
                r < pWin + 0.25 -> MatchOutcome.DRAW
                else -> MatchOutcome.LOSS
            }
        }
        val gs = (1.0 + (seed.rating - 70) / 20.0 + rng.nextDouble(-0.15, 0.15)).coerceIn(0.7, 2.7)
        val gc = (1.6 - (seed.rating - 70) / 25.0 + rng.nextDouble(-0.15, 0.15)).coerceIn(0.5, 2.2)
        val rest = if (rng.nextDouble() < 0.2) 3 else 7 // occasional midweek fatigue
        return TeamProfile(
            id = seed.id, name = seed.name, shortName = seed.short, leaguePosition = position,
            recentForm = form, overallRating = seed.rating,
            goalsScoredAvg = (gs * 10).toInt() / 10.0, goalsConcededAvg = (gc * 10).toInt() / 10.0,
            daysSinceLastMatch = rest,
            koreanName = seed.korean, crestPrimary = seed.primary, crestSecondary = seed.secondary,
        )
    }

    private fun genH2H(home: TeamSeed, away: TeamSeed): HeadToHead {
        val rng = Random((home.id + away.id).hashCode())
        val n = rng.nextInt(2, 6)
        val edge = ((home.rating - away.rating) / 40.0) // stronger side tends to lead the record
        return HeadToHead(
            List(n) { i ->
                val r = rng.nextDouble() + edge
                val outcome = when {
                    r > 0.60 -> H2HOutcome.HOME_WIN
                    r > 0.30 -> H2HOutcome.DRAW
                    else -> H2HOutcome.AWAY_WIN
                }
                H2HMeeting(outcome, atHomeVenue = i % 2 == 0)
            },
        )
    }

    /**
     * Circle-method round robin for [n] teams; returns [rounds] rounds, each a list of (home, away)
     * index pairs. Deterministic and self-play-free.
     */
    private fun roundRobin(n: Int, rounds: Int): List<List<Pair<Int, Int>>> {
        val arr = ArrayDeque((0 until n).toList())
        val schedule = mutableListOf<List<Pair<Int, Int>>>()
        repeat(rounds) { r ->
            val list = arr.toList()
            val pairs = (0 until n / 2).map { i ->
                val a = list[i]
                val b = list[n - 1 - i]
                if (r % 2 == 0) a to b else b to a // alternate home/away for variety
            }
            schedule.add(pairs)
            // rotate all but the first element
            val fixed = arr.removeFirst()
            val last = arr.removeLast()
            arr.addFirst(last)
            arr.addFirst(fixed)
        }
        return schedule
    }

    // ---- small builders -------------------------------------------------------------------------

    private fun p(
        id: String, name: String, korean: String, short: String, primary: Long, secondary: Long,
        pos: Int, rating: Double, form: String, gs: Double, gc: Double, rest: Int,
    ) = TeamProfile(
        id = id, name = name, shortName = short, leaguePosition = pos,
        recentForm = form.map {
            when (it) {
                'W' -> MatchOutcome.WIN
                'D' -> MatchOutcome.DRAW
                else -> MatchOutcome.LOSS
            }
        },
        overallRating = rating, goalsScoredAvg = gs, goalsConcededAvg = gc, daysSinceLastMatch = rest,
        koreanName = korean, crestPrimary = primary, crestSecondary = secondary,
    )

    private fun won(atVenue: Boolean) = H2HMeeting(H2HOutcome.HOME_WIN, atVenue)
    private fun drew(atVenue: Boolean) = H2HMeeting(H2HOutcome.DRAW, atVenue)
    private fun lost(atVenue: Boolean) = H2HMeeting(H2HOutcome.AWAY_WIN, atVenue)
}
