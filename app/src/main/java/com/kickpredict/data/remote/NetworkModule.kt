package com.kickpredict.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kickpredict.data.remote.apifootball.ApiFootballApi
import com.kickpredict.data.remote.football.FootballDataApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Builds the Retrofit stack (OkHttp + kotlinx.serialization) for the live data providers. Kept as a
 * small factory so it can be provided by the DI container and later replaced by Hilt/Koin without
 * churn. Each provider authenticates with its own header, injected by an interceptor.
 */
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun client(auth: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()
    }

    /** Auth via an HTTP header (football-data.org's `X-Auth-Token`). */
    private fun headerAuth(header: String, key: String) = Interceptor { chain ->
        val request = if (key.isNotBlank()) {
            chain.request().newBuilder().addHeader(header, key).build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    /** Auth via a query parameter (APIFootball's `APIkey`). */
    private fun queryAuth(param: String, key: String) = Interceptor { chain ->
        val request = chain.request()
        val authed = if (key.isNotBlank()) {
            request.newBuilder().url(request.url.newBuilder().addQueryParameter(param, key).build()).build()
        } else {
            request
        }
        chain.proceed(authed)
    }

    private inline fun <reified T> api(baseUrl: String, auth: Interceptor): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client(auth))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(T::class.java)

    fun footballDataApi(key: String): FootballDataApi =
        api(FootballDataApi.BASE_URL, headerAuth("X-Auth-Token", key))

    fun apiFootballApi(key: String): ApiFootballApi =
        api(ApiFootballApi.BASE_URL, queryAuth("APIkey", key))
}
