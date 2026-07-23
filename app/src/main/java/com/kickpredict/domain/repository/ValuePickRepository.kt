package com.kickpredict.domain.repository

import com.kickpredict.domain.model.ValuePick

/**
 * Persists the value-pick ledger. Storage only knows the pick and its flag-time price; settlement
 * (won/lost/pending) is derived elsewhere by joining against recorded results, so [all] returns
 * picks as [com.kickpredict.domain.model.ValuePickStatus.PENDING].
 */
interface ValuePickRepository {
    /** Log newly-flagged picks; already-logged matches keep their original price. */
    suspend fun record(picks: List<ValuePick>)

    suspend fun all(): List<ValuePick>

    suspend fun clear()
}
