package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.TeamEntity

@Dao
interface TeamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(teams: List<TeamEntity>)

    @Query("SELECT * FROM teams")
    suspend fun getAll(): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: String): TeamEntity?
}
