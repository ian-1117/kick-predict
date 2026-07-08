package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The user-entered final score of a fixture — the ground truth for scoring & calibration. */
@Entity(tableName = "match_result")
data class MatchResultEntity(
    @PrimaryKey val matchId: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val recordedAt: Long,
)
