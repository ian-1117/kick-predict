package com.kickpredict.data.repository

import com.kickpredict.data.local.cache.CachedMatch
import com.kickpredict.data.local.cache.toCached
import com.kickpredict.data.local.cache.toDomain
import com.kickpredict.data.local.dao.FixtureCacheDao
import com.kickpredict.data.local.dao.TeamDao
import com.kickpredict.data.local.entity.FixtureCacheEntity
import com.kickpredict.data.local.entity.TeamEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.remote.live.LiveFixtureRemoteSource
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
    private val fixtureCacheDao: FixtureCacheDao,
    // Offline seed source; defaults to bundled mock fixtures, overridden with real data when available.
    private val offlineFallback: () -> List<Match> = { MockDataProvider.matches() },
    // Rebuilds fixtures with profiles as of a matchday; only the bundled historical data can do this.
    private val asOfFallback: ((Int) -> List<Match>)? = null,
    private val cacheTtlMillis: Long = 15 * 60 * 1000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) : MatchRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(CachedMatch.serializer())

    private val mutex = Mutex()
    @Volatile private var cached: List<Match>? = null
    @Volatile private var cachedAt: Long = 0L

    override suspend fun getMatches(forceRefresh: Boolean): List<Match> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cached?.takeIf { now() - cachedAt < cacheTtlMillis }?.let { return@withContext it }
        mutex.withLock {
            if (!forceRefresh) cached?.takeIf { now() - cachedAt < cacheTtlMillis }?.let { return@withContext it }
            val live = runCatching { liveSource.getFixtures() }.getOrNull()?.takeIf { it.isNotEmpty() }
            val matches = live ?: cachedMatches()?.takeIf { it.isNotEmpty() } ?: offlineFallback()
            if (live != null) persist(live)
            cacheTeams(matches)
            cached = matches
            cachedAt = now()
            matches
        }
    }

    /** Last persisted fixtures, deserialized from Room — no network, for an instant first paint. */
    override suspend fun cachedMatches(): List<Match>? = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val entity = fixtureCacheDao.get(CACHE_KEY) ?: return@withContext null
        runCatching { json.decodeFromString(listSerializer, entity.json).map { it.toDomain() } }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun persist(matches: List<Match>) {
        runCatching {
            val payload = json.encodeToString(listSerializer, matches.map { it.toCached() })
            fixtureCacheDao.upsert(FixtureCacheEntity(CACHE_KEY, payload, now()))
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

    private companion object {
        const val CACHE_KEY = "live_fixtures"
    }
}
