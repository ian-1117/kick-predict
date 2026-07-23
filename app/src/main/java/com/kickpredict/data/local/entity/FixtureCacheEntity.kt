package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches the last successfully fetched fixtures payload (serialized DTO JSON) so the app works
 * offline. Keyed by a fixed key since we cache the whole fixtures list as one blob.
 */
@Entity(tableName = "fixture_cache")
data class FixtureCacheEntity(
    @PrimaryKey val id: String,
    val json: String,
    val savedAt: Long,
)
