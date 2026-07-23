package com.kickpredict.data

import com.kickpredict.domain.rating.EloModel
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Trains our [EloModel] on **real historical results** (football-data.co.uk CSVs in test resources)
 * and evaluates it out-of-sample. Hyper-parameters (K, home advantage, between-season carry-over) are
 * chosen on a **validation** window (2021-22 & 2022-23) and only then reported on the untouched
 * **test** season (2023-24) — so nothing is fitted to the numbers we quote. Uses margin-of-victory
 * weighting and season regression. Real 1X2 tops out ~55-58%, so this is the honest ceiling.
 */
class RealDataEloTest {

    private data class Game(val home: String, val away: String, val hg: Int, val ag: Int) {
        val outcome: Int get() = if (hg > ag) 0 else if (hg < ag) 2 else 1 // home / draw / away
    }

    private data class Cfg(val k: Double, val homeAdv: Double, val carry: Double)
    private data class Score(var correct: Int = 0, var baseline: Int = 0, var n: Int = 0, var logLoss: Double = 0.0)

    private val leagues = linkedMapOf("E0" to "EPL", "SP1" to "LaLiga", "I1" to "SerieA", "D1" to "Bundesliga")
    private val seasons = listOf("2021", "2122", "2223", "2324")
    private val validationSeasons = setOf("2122", "2223")
    private val testSeason = setOf("2324")

    @Test
    fun `tuned Elo on real past seasons beats the home-win baseline out-of-sample`() {
        val cache = leagues.keys.associateWith { code -> seasons.associateWith { load(code, it) } }
        assumeTrue(
            "real-data CSVs not present under src/test/resources/realdata — skipping (see class docs to fetch)",
            cache.values.sumOf { s -> s.values.sumOf { it.size } } > 1000,
        )

        // --- Hyper-parameter search on the validation window only --------------------------------
        val grid = buildList {
            for (k in listOf(18.0, 24.0, 32.0, 40.0))
                for (ha in listOf(50.0, 65.0, 80.0, 100.0))
                    for (carry in listOf(0.75, 0.85, 1.0)) add(Cfg(k, ha, carry))
        }
        val best = grid.minByOrNull { cfg ->
            val s = Score()
            leagues.keys.forEach { code -> evalLeague(cache.getValue(code), cfg, validationSeasons, s) }
            s.logLoss / s.n
        }!!

        // --- Report on the untouched test season ------------------------------------------------
        val total = Score()
        val perLeague = StringBuilder()
        leagues.forEach { (code, name) ->
            val s = Score()
            evalLeague(cache.getValue(code), best, testSeason, s)
            total.correct += s.correct; total.baseline += s.baseline; total.n += s.n; total.logLoss += s.logLoss
            perLeague.append(" $name(elo=${pct(s.correct, s.n)}%/home=${pct(s.baseline, s.n)}%)")
        }

        println(
            "REALDATA_TUNED cfg[K=${best.k} homeAdv=${best.homeAdv} carry=${best.carry}]" +
                " testGames=${total.n} | Elo=${pct(total.correct, total.n)}%" +
                " homeWinBaseline=${pct(total.baseline, total.n)}% logloss=${fmt(total.logLoss / total.n)} |$perLeague",
        )
        assertTrue("should have loaded real games", total.n > 1000)
        assertTrue("tuned Elo should beat the naive home-win baseline", total.correct > total.baseline)
    }

    /** Walk-forward one league with [cfg]; scores only games whose season is in [evalSeasons]. */
    private fun evalLeague(bySeason: Map<String, List<Game>>, cfg: Cfg, evalSeasons: Set<String>, into: Score) {
        val elo = EloModel(kFactor = cfg.k, homeAdvantage = cfg.homeAdv, marginOfVictory = true)
        seasons.forEachIndexed { index, season ->
            if (index > 0) elo.regressToMean(cfg.carry)
            val evaluating = season in evalSeasons
            bySeason.getValue(season).forEach { g ->
                if (evaluating) {
                    val (pH, pD, pA) = elo.predict(g.home, g.away)
                    val probs = listOf(pH, pD, pA)
                    if (probs.indexOf(probs.max()) == g.outcome) into.correct++
                    if (g.outcome == 0) into.baseline++
                    into.logLoss += -ln(probs[g.outcome].coerceAtLeast(1e-9))
                    into.n++
                }
                elo.update(g.home, g.away, g.hg, g.ag)
            }
        }
    }

    private fun load(code: String, season: String): List<Game> {
        val file = listOf(
            java.io.File("src/main/assets/realdata/${code}_$season.csv"),
            java.io.File("app/src/main/assets/realdata/${code}_$season.csv"),
        ).firstOrNull { it.exists() } ?: return emptyList()
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val header = lines.first().split(",")
        val hi = header.indexOf("HomeTeam"); val ai = header.indexOf("AwayTeam")
        val hgi = header.indexOf("FTHG"); val agi = header.indexOf("FTAG")
        if (listOf(hi, ai, hgi, agi).any { it < 0 }) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = line.split(",")
            if (maxOf(hi, ai, hgi, agi) >= f.size) return@mapNotNull null
            val hg = f[hgi].toIntOrNull() ?: return@mapNotNull null
            val ag = f[agi].toIntOrNull() ?: return@mapNotNull null
            val home = f[hi].trim(); val away = f[ai].trim()
            if (home.isEmpty() || away.isEmpty()) null else Game(home, away, hg, ag)
        }
    }

    private fun pct(c: Int, n: Int) = if (n == 0) 0 else (c.toDouble() / n * 100).roundToInt()
    private fun fmt(v: Double) = (v * 1000).roundToInt() / 1000.0
}
