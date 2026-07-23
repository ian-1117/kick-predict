package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.ValuePickEntity

@Dao
interface ValuePickDao {

    /** Lock the first-seen price: ignore if this match was already flagged, so odds don't drift. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(pick: ValuePickEntity)

    /** Track the closing line: the latest observed price, updated on every scan until kickoff. */
    @Query("UPDATE value_pick SET closingOdds = :closingOdds WHERE matchId = :matchId")
    suspend fun updateClosing(matchId: String, closingOdds: Double)

    @Query("SELECT * FROM value_pick")
    suspend fun getAll(): List<ValuePickEntity>

    @Query("DELETE FROM value_pick")
    suspend fun deleteAll()
}
