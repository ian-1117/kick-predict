package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.PredictionLogEntity

@Dao
interface PredictionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<PredictionLogEntity>)

    @Query("SELECT * FROM prediction_log")
    suspend fun getAll(): List<PredictionLogEntity>

    @Query("SELECT * FROM prediction_log WHERE matchId = :id")
    suspend fun getById(id: String): PredictionLogEntity?
}
