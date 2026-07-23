package com.kickpredict.data

import com.kickpredict.domain.rating.EloModel
import com.kickpredict.domain.rating.PoissonRatings
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Builds up the engine's goal model on **real data**, one tuning point at a time, each chosen on a
 * validation window (2021-22 & 2022-23) and reported on the untouched test season (2023-24):
 *   1. Dixon-Coles Poisson attack/defence + low-score correction (ρ)
 *   2. + home/away split ratings
 *   3. + between-season regression (recency / roster turnover)
 *   4. + ensemble with the learned Elo model
 * Prints the accuracy/log-loss progression so the payoff of each step is visible.
 */
class PoissonRatingsRealDataTest {

    private data class Game(val home: String, val away: String, val hg: Int, val ag: Int) {
        val outcome: Int get() = if (hg > ag) 0 else if (hg < ag) 2 else 1
    }
    private data class PCfg(val lr: Double, val homeAdv: Double, val base: Double, val rho: Double, val split: Boolean, val carry: Double)
    private class Score { var correct = 0; var baseline = 0; var n = 0; var loss = 0.0 }

    private val leagues = listOf("E0", "SP1", "I1", "D1")
    private val seasons = listOf("2021", "2122", "2223", "2324")
    private val validation = setOf("2122", "2223")
    private val test = setOf("2324")

    @Test
    fun `dixon-coles poisson on real data, tuning points 1 to 4`() {
        val cache = leagues.associateWith { c -> seasons.associateWith { load(c, it) } }
        assumeTrue(
            "real-data CSVs not present under src/test/resources/realdata — skipping (see class docs to fetch)",
            cache.values.sumOf { s -> s.values.sumOf { it.size } } > 1000,
        )
        fun ll(s: Score) = s.loss / s.n

        // Stage 1: Dixon-Coles attack/defence (no split)
        val g1 = buildList {
            for (lr in listOf(0.04, 0.06, 0.08)) for (ha in listOf(0.2, 0.3))
                for (b in listOf(0.15, 0.25)) for (rho in listOf(-0.08, 0.0))
                    add(PCfg(lr, ha, b, rho, split = false, carry = 1.0))
        }
        val b1 = g1.minByOrNull { ll(evalPoisson(it, validation, cache)) }!!
        val t1 = evalPoisson(b1, test, cache)

        // Stage 2: + home/away split
        val g2 = buildList {
            for (lr in listOf(0.04, 0.06, 0.08)) for (b in listOf(0.15, 0.25)) for (rho in listOf(-0.08, 0.0))
                add(PCfg(lr, 0.0, b, rho, split = true, carry = 1.0))
        }
        val b2 = g2.minByOrNull { ll(evalPoisson(it, validation, cache)) }!!
        val t2 = evalPoisson(b2, test, cache)

        // Stage 3: + between-season regression
        val g3 = listOf(0.75, 0.85, 0.95, 1.0).map { b2.copy(carry = it) }
        val b3 = g3.minByOrNull { ll(evalPoisson(it, validation, cache)) }!!
        val t3 = evalPoisson(b3, test, cache)

        // Stage 4: + ensemble with Elo
        val weights = listOf(0.4, 0.55, 0.7, 0.85)
        val b4w = weights.minByOrNull { ll(evalEnsemble(b3, it, validation, cache)) }!!
        val t4 = evalEnsemble(b3, b4w, test, cache)

        fun line(tag: String, s: Score) = "$tag=${pct(s.correct, s.n)}%(ll=${fmt(ll(s))})"
        println(
            "POISSON_STAGES testGames=${t1.n} homeBaseline=${pct(t1.baseline, t1.n)}% | " +
                line("1_DixonColes", t1) + " " + line("2_HomeAwaySplit", t2) + " " +
                line("3_SeasonRegress", t3) + " " + line("4_EnsembleElo(w=$b4w)", t4),
        )
        assertTrue("stage 1 beats baseline", t1.correct > t1.baseline)
        assertTrue("ensemble beats baseline", t4.correct > t4.baseline)
    }

    private fun evalPoisson(cfg: PCfg, evalSeasons: Set<String>, cache: Map<String, Map<String, List<Game>>>): Score {
        val agg = Score()
        leagues.forEach { code ->
            val model = PoissonRatings(learningRate = cfg.lr, homeAdvantage = cfg.homeAdv, baseRate = cfg.base, rho = cfg.rho, homeAwaySplit = cfg.split)
            seasons.forEachIndexed { idx, season ->
                if (idx > 0 && cfg.carry < 1.0) model.regressToMean(cfg.carry)
                val scoring = season in evalSeasons
                cache.getValue(code).getValue(season).forEach { g ->
                    if (scoring) score(model.predict(g.home, g.away), g, agg)
                    model.update(g.home, g.away, g.hg, g.ag)
                }
            }
        }
        return agg
    }

    private fun evalEnsemble(cfg: PCfg, weight: Double, evalSeasons: Set<String>, cache: Map<String, Map<String, List<Game>>>): Score {
        val agg = Score()
        leagues.forEach { code ->
            val poisson = PoissonRatings(learningRate = cfg.lr, homeAdvantage = cfg.homeAdv, baseRate = cfg.base, rho = cfg.rho, homeAwaySplit = cfg.split)
            val elo = EloModel(kFactor = 20.0, homeAdvantage = 60.0, marginOfVictory = true)
            seasons.forEachIndexed { idx, season ->
                if (idx > 0 && cfg.carry < 1.0) poisson.regressToMean(cfg.carry)
                val scoring = season in evalSeasons
                cache.getValue(code).getValue(season).forEach { g ->
                    if (scoring) {
                        val (pH, pD, pA) = poisson.predict(g.home, g.away)
                        val (eH, eD, eA) = elo.predict(g.home, g.away)
                        val blended = listOf(weight * pH + (1 - weight) * eH, weight * pD + (1 - weight) * eD, weight * pA + (1 - weight) * eA)
                        val s = blended.sum()
                        score(Triple(blended[0] / s, blended[1] / s, blended[2] / s), g, agg)
                    }
                    poisson.update(g.home, g.away, g.hg, g.ag)
                    elo.update(g.home, g.away, g.hg, g.ag)
                }
            }
        }
        return agg
    }

    private fun score(probs: Triple<Double, Double, Double>, g: Game, into: Score) {
        val p = listOf(probs.first, probs.second, probs.third)
        if (p.indexOf(p.max()) == g.outcome) into.correct++
        if (g.outcome == 0) into.baseline++
        into.loss += -ln(p[g.outcome].coerceAtLeast(1e-9))
        into.n++
    }

    private fun load(code: String, season: String): List<Game> {
        val file = listOf(
            java.io.File("src/main/assets/realdata/${code}_$season.csv"),
            java.io.File("app/src/main/assets/realdata/${code}_$season.csv"),
        ).firstOrNull { it.exists() } ?: return emptyList()
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val h = lines.first().split(",")
        val hi = h.indexOf("HomeTeam"); val ai = h.indexOf("AwayTeam"); val hgi = h.indexOf("FTHG"); val agi = h.indexOf("FTAG")
        if (listOf(hi, ai, hgi, agi).any { it < 0 }) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = line.split(",")
            if (maxOf(hi, ai, hgi, agi) >= f.size) return@mapNotNull null
            val hg = f[hgi].toIntOrNull() ?: return@mapNotNull null
            val ag = f[agi].toIntOrNull() ?: return@mapNotNull null
            if (f[hi].isBlank() || f[ai].isBlank()) null else Game(f[hi].trim(), f[ai].trim(), hg, ag)
        }
    }

    private fun pct(c: Int, n: Int) = if (n == 0) 0 else (c.toDouble() / n * 100).roundToInt()
    private fun fmt(v: Double) = (v * 1000).roundToInt() / 1000.0
}
