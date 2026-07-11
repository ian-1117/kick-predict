package com.kickpredict.data.repository

import com.kickpredict.data.local.dao.TeamDao
import com.kickpredict.data.local.entity.TeamEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.remote.live.LiveFixtureRemoteSource
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fixture repository backed by the live data providers ([LiveFixtureRemoteSource]) with the bundled
 * historical seasons as an offline safety net:
 *  1. fetch the current season live (per-league hybrid; missing keys fall back to bundled inside the
 *     source itself),
 *  2. memoize the result for the session so repeated reads don't re-hit rate-limited APIs,
 *  3. if the whole fetch yields nothing, seed from bundled data so the UI is never empty.
 *
 * Teams are also written to Room on every successful load (team/form caching).
 */
class MatchRepositoryImpl(
    private val liveSource: LiveFixtureRemoteSource,
    private val teamDao: TeamDao,
    // Offline seed source; defaults to bundled mock fixtures, overridden with real data when available.
    private val offlineFallback: () -> List<Match> = { MockDataProvider.matches() },
    // Rebuilds fixtures with profiles as of a matchday; only the bundled historical data can do this.
    private val asOfFallback: ((Int) -> List<Match>)? = null,
    private val cacheTtlMillis: Long = 15 * 60 * 1000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) : MatchRepository {

    private val mutex = Mutex()
    @Volatile private var cached: List<Match>? = null
    @Volatile private var cachedAt: Long = 0L

    override suspend fun getMatches(): List<Match> = withContext(Dispatchers.IO) {
        cached?.takeIf { now() - cachedAt < cacheTtlMillis }?.let { return@withContext it }
        mutex.withLock {
            cached?.takeIf { now() - cachedAt < cacheTtlMillis }?.let { return@withContext it }
            val matches = runCatching { liveSource.getFixtures() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: offlineFallback()
            cacheTeams(matches)
            cached = matches
            cachedAt = now()
            matches
        }
    }

    override suspend fun getMatch(id: String): Match? =
        getMatches().firstOrNull { it.id == id }

    override suspend fun getMatchesAsOf(round: Int): List<Match> = withContext(Dispatchers.IO) {
        asOfFallback?.invoke(round) ?: getMatches().filter { it.round >= round }
    }

    private suspend fun cacheTeams(matches: List<Match>) {
        val entities = matches.flatMap { match ->
            listOf(
                TeamEntity.from(match.league, match.homeTeam),
                TeamEntity.from(match.league, match.awayTeam),
            )
        }
        teamDao.upsertAll(entities)
    }
}
