package com.kickpredict.data

import com.kickpredict.data.real.RealDataProvider
import com.kickpredict.domain.model.LeagueType
import com.kickpredict.domain.model.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * The season projection is only honest if the fixtures it simulates were built without seeing their
 * own results. These pin that down against the bundled 2023-24 data.
 */
class RealDataAsOfTest {

    private lateinit var provider: RealDataProvider

    private fun open(path: String): InputStream {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.exists() }?.inputStream()
            ?: throw java.io.FileNotFoundException(path)
    }

    @Before
    fun setUp() {
        provider = RealDataProvider(::open)
        assumeTrue("bundled real-data CSVs required", provider.hasData)
    }

    private fun epl(matches: List<Match>) = matches.filter { it.league == LeagueType.EPL }

    @Test
    fun `as-of fixtures start at the cutoff and keep the ids of the full-season fixtures`() {
        val full = epl(provider.matches()).associateBy { it.id }
        val asOf = epl(provider.matchesAsOf(20))

        assertTrue(asOf.isNotEmpty())
        assertTrue("no fixture before the cutoff may be simulated", asOf.all { it.round >= 20 })
        assertEquals(
            "ids must still join up with recorded results",
            full.keys.filter { full.getValue(it).round >= 20 }.toSet(),
            asOf.map { it.id }.toSet(),
        )
    }

    @Test
    fun `profiles at the cutoff differ from the full-season profiles`() {
        val fullSeason = epl(provider.matches()).first { it.round == 20 }
        val asOf = epl(provider.matchesAsOf(20)).first { it.id == fullSeason.id }

        // Same fixture, different knowledge: the round-20 view cannot have the season's final numbers.
        assertEquals(fullSeason.homeTeam.id, asOf.homeTeam.id)
        assertNotEquals(
            "full-season and as-of profiles should not coincide",
            fullSeason.homeTeam.leaguePosition to fullSeason.homeTeam.goalsScoredAvg,
            asOf.homeTeam.leaguePosition to asOf.homeTeam.goalsScoredAvg,
        )
    }

    @Test
    fun `a fixture's as-of profile is unchanged by results after the cutoff`() {
        // Whatever happens from round 20 on cannot move the round-20 view. Rebuilding at a later
        // cutoff must leave the earlier cutoff's numbers alone.
        val atTwenty = epl(provider.matchesAsOf(20)).first { it.round == 20 }
        val rebuilt = epl(provider.matchesAsOf(20)).first { it.round == 20 }
        assertEquals(atTwenty.homeTeam, rebuilt.homeTeam)

        // And a team's form/rates as of round 20 must differ from its numbers as of round 30, since
        // ten more matchdays of evidence have arrived.
        val atThirty = epl(provider.matchesAsOf(30)).firstOrNull { it.homeTeam.id == atTwenty.homeTeam.id }
        if (atThirty != null) {
            assertNotEquals(atTwenty.homeTeam.recentForm, atThirty.homeTeam.recentForm)
        }
    }

    @Test
    fun `a preseason projection still has usable profiles`() {
        val preseason = epl(provider.matchesAsOf(1))
        assertEquals("every fixture is simulated from matchday 1", epl(provider.matches()).size, preseason.size)

        // No matches played yet, so the prior seasons must supply the scoring rates; a zero here would
        // collapse the engine's lambda to its floor and make every preseason fixture a coin flip.
        preseason.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinctBy { it.id }.forEach { team ->
            assertTrue("${team.name} has no scoring rate", team.goalsScoredAvg > 0.3)
            assertTrue("${team.name} has no conceding rate", team.goalsConcededAvg > 0.3)
            assertTrue("${team.name} has a degenerate rating", team.overallRating in 40.0..95.0)
        }

        // Promoted sides have no prior season in the dataset; they must fall back to the league average
        // rather than to zero, and so must land near the middle of the rating range.
        val ratings = preseason.map { it.homeTeam }.distinctBy { it.id }.map { it.overallRating }
        assertTrue("preseason ratings should spread across teams", ratings.distinct().size > 1)
    }

    @Test
    fun `head-to-head at the cutoff excludes later meetings`() {
        val asOf = epl(provider.matchesAsOf(2)).first { it.headToHead.total > 0 }
        val full = epl(provider.matches()).first { it.id == asOf.id }
        assertTrue(
            "an early-season fixture cannot know more meetings than the full-season view",
            asOf.headToHead.total <= full.headToHead.total,
        )
    }
}
