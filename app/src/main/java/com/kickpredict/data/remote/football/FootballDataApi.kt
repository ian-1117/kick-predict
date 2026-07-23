package com.kickpredict.data.remote.football

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * football-data.org v4 endpoints for the four European leagues the app covers. Auth is the
 * `X-Auth-Token` header, added by an OkHttp interceptor (see NetworkModule).
 *
 * Competition codes: PL = Premier League, PD = LaLiga, SA = Serie A, BL1 = Bundesliga.
 */
interface FootballDataApi {

    @GET("v4/competitions/{code}/matches")
    suspend fun matches(
        @Path("code") code: String,
        @Query("season") season: Int,
    ): FdMatchesResponse

    @GET("v4/competitions/{code}/standings")
    suspend fun standings(
        @Path("code") code: String,
        @Query("season") season: Int,
    ): FdStandingsResponse

    companion object {
        const val BASE_URL = "https://api.football-data.org/"
    }
}
