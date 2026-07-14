package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.ParlayLegEntity
import com.kickpredict.data.local.entity.SavedParlayEntity

@Dao
interface SavedParlayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParlay(parlay: SavedParlayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegs(legs: List<ParlayLegEntity>)

    @Query("SELECT * FROM saved_parlay ORDER BY createdAt DESC")
    suspend fun getParlays(): List<SavedParlayEntity>

    @Query("SELECT * FROM parlay_leg")
    suspend fun getLegs(): List<ParlayLegEntity>

    @Query("DELETE FROM saved_parlay WHERE id = :id")
    suspend fun deleteParlay(id: String)

    @Query("DELETE FROM parlay_leg WHERE parlayId = :id")
    suspend fun deleteLegs(id: String)
}
