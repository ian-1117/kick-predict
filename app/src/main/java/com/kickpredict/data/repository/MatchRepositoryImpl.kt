package com.kickpredict.data.repository

import com.kickpredict.data.local.dao.FixtureCacheDao
import com.kickpredict.data.local.dao.TeamDao
import com.kickpredict.data.local.entity.FixtureCacheEntity
import com.kickpredict.data.local.entity.TeamEntity
import com.kickpredict.data.mock.MockDataProvider
import com.kickpredict.data.remote.PredictionApi
import com.kickpredict.data.remote.dto.MatchDto
import com.kickpredict.data.remote.dto.toDomain
import com.kickpredict.domain.model.Match
import com.kickpredict.domain.repository.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Offline-first repository:
 *  1. try the live [PredictionApi]; on success cache the payload and use it,
 *  2. on failure fall back to the last cached payload (works offline),
 *  3. if there is no cache yet, seed from bundled [MockDataProvider] so the app is never empty.
 *
 * Teams are also written to Room on every successful load (team/form caching).
 */
class MatchRepositoryImpl(
    private val api: PredictionApi,
    private val json: Json,
    private val fixtureCacheDao: FixtureCacheDao,
    private val teamDao: TeamDao,
    // Offline seed source; defaults to bundled mock fixtures, overridden with real data when available.
    private val offlineFallback: () -> List<Match> = { MockDataProvider.matches() },
    // Rebuilds fixtures with profiles as of a matchday; only the bundled historical data can do this.
    private val asOfFallback: ((Int) -> List<Match>)? = null,
) : MatchRepository {

    private val listSerializer = ListSerializer(MatchDto.serializer())

    override suspend fun getMatches(): List<Match> = withContext(Dispatchers.IO) {
        val matches = loadMatches()
        cacheTeams(matches)
        matches
    }

    override suspend fun getMatch(id: String): Match? =
        getMatches().firstOrNull { it.id == id }

    override suspend fun getMatchesAsOf(round: Int): List<Match> = withContext(Dispatchers.IO) {
        asOfFallback?.invoke(round) ?: getMatches().filter { it.round >= round }
    }

    private suspend fun loadMatches(): List<Match> {
        try {
            val dtos = api.getFixtures()
            fixtureCacheDao.upsert(
                FixtureCacheEntity(
                    id = CACHE_KEY,
                    json = json.encodeToString(listSerializer, dtos),
                    savedAt = System.currentTimeMillis(),
                ),
            )
            return dtos.map { it.toDomain() }
        } catch (networkError: Exception) {
            // Offline / API unavailable — fall back to the last good cache.
            val cached = fixtureCacheDao.get(CACHE_KEY)
            if (cached != null) {
                runCatching { json.decodeFromString(listSerializer, cached.json).map { it.toDomain() } }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
            // Never leave the UI empty: seed from the offline source (real data if bundled, else mock).
            return offlineFallback()
        }
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
        const val CACHE_KEY = "all_fixtures"
    }
}
