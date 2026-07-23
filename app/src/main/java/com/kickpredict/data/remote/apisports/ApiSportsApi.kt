package com.kickpredict.data.remote.apisports

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API-Sports (v3.football.api-sports.io) endpoints for the two K League tiers. Auth is the
 * `x-apisports-key` header (added by an interceptor, see NetworkModule).
 *
 * League ids: 292 = K League 1, 293 = K League 2. `season` is the calendar year (K League runs
 * Feb–Nov in a single year). On the free tier only the current season is available.
 */
interface ApiSportsApi {

    /** Every fixture (played + scheduled) for a league's season. */
    @GET("fixtures")
    suspend fun fixtures(
        @Query("league") league: Int,
        @Query("season") season: Int,
    ): AsFixturesResponse

    /** The current standings table for a league's season. */
    @GET("standings")
    suspend fun standings(
        @Query("league") league: Int,
        @Query("season") season: Int,
    ): AsStandingsResponse

    companion object {
        const val BASE_URL = "https://v3.football.api-sports.io/"
        const val K_LEAGUE_1 = 292
        const val K_LEAGUE_2 = 293
    }
}
