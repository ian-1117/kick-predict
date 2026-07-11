package com.kickpredict.data.remote.apifootball

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * APIFootball (apiv3.apifootball.com) endpoints for the two K League tiers. This is a flat,
 * action-based API: every call hits the root with `?action=...`, and auth is the `APIkey` query
 * parameter (appended by an interceptor, see NetworkModule).
 *
 * League ids: 219 = K League 1, 218 = K League 2.
 */
interface ApiFootballApi {

    /** Finished + scheduled fixtures in a date range for one league (a full season with a wide range). */
    @GET(".")
    suspend fun events(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("league_id") leagueId: Int,
        @Query("action") action: String = "get_events",
    ): List<AfEvent>

    /** Current-season standings for one league. */
    @GET(".")
    suspend fun standings(
        @Query("league_id") leagueId: Int,
        @Query("action") action: String = "get_standings",
    ): List<AfStanding>

    companion object {
        const val BASE_URL = "https://apiv3.apifootball.com/"
        const val K_LEAGUE_1 = 219
        const val K_LEAGUE_2 = 218
    }
}
