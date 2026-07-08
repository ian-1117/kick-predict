package com.kickpredict.data.remote

import com.kickpredict.data.remote.dto.MatchDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit endpoint definition for the (future) live fixtures/prediction service.
 * Not exercised by the current mock-data path, but wired and ready to switch on.
 */
interface PredictionApi {

    @GET("v1/fixtures")
    suspend fun getFixtures(
        @Query("leagues") leagues: String = "EPL,LALIGA,SERIE_A,BUNDESLIGA,K_LEAGUE",
    ): List<MatchDto>

    companion object {
        const val BASE_URL = "https://api.kick-predict.example/"
    }
}
