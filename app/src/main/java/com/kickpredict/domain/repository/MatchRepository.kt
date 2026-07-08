package com.kickpredict.domain.repository

import com.kickpredict.domain.model.Match

/**
 * Source of fixtures for the app. The current implementation is backed by mock data;
 * a Retrofit-backed implementation can be swapped in without touching the UI or use cases.
 */
interface MatchRepository {
    suspend fun getMatches(): List<Match>
    suspend fun getMatch(id: String): Match?
}
