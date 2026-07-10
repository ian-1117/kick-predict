package com.kickpredict.domain.repository

import com.kickpredict.domain.model.Match

/**
 * Source of fixtures for the app. The current implementation is backed by mock data;
 * a Retrofit-backed implementation can be swapped in without touching the UI or use cases.
 */
interface MatchRepository {
    suspend fun getMatches(): List<Match>
    suspend fun getMatch(id: String): Match?

    /**
     * Fixtures from [round] onwards, with team profiles built only from what was known before it —
     * the out-of-sample view a season projection needs.
     *
     * The default simply filters [getMatches], whose profiles reflect the full season; a source that
     * can rebuild profiles as of a point in time (the bundled historical data) overrides this.
     */
    suspend fun getMatchesAsOf(round: Int): List<Match> = getMatches().filter { it.round >= round }
}
