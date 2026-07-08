package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.MatchResultEntity

@Dao
interface MatchResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: MatchResultEntity)

    @Query("SELECT * FROM match_result WHERE matchId = :id")
    suspend fun getById(id: String): MatchResultEntity?

    @Query("SELECT * FROM match_result")
    suspend fun getAll(): List<MatchResultEntity>

    @Query("SELECT COUNT(*) FROM match_result")
    suspend fun count(): Int

    @Query("DELETE FROM match_result WHERE matchId = :id")
    suspend fun delete(id: String)
}
