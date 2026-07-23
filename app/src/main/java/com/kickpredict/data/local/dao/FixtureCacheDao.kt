package com.kickpredict.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kickpredict.data.local.entity.FixtureCacheEntity

@Dao
interface FixtureCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FixtureCacheEntity)

    @Query("SELECT * FROM fixture_cache WHERE id = :id")
    suspend fun get(id: String): FixtureCacheEntity?
}
