package com.kickpredict.domain.repository

import com.kickpredict.domain.model.SavedParlay

/**
 * Persists saved parlays. Storage only knows the bet as placed (legs + frozen combined price);
 * settlement is derived elsewhere by joining the legs against recorded results.
 */
interface ParlayRepository {
    suspend fun save(parlay: SavedParlay)

    suspend fun all(): List<SavedParlay>

    suspend fun delete(id: String)
}
