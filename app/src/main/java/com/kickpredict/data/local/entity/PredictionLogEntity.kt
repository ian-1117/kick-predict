package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kickpredict.domain.model.LeagueType

/**
 * A snapshot of a prediction the engine produced, logged so it can later be scored against the
 * real result and fed into calibration. One row per match (latest prediction wins).
 */
@Entity(tableName = "prediction_log")
data class PredictionLogEntity(
    @PrimaryKey val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val predictedOutcome: String, // PredictedOutcome.name
    val confidence: Int,
    val homeWinPercent: Int,
    val drawPercent: Int,
    val awayWinPercent: Int,
    val expectedHomeGoals: Double,
    val expectedAwayGoals: Double,
    val predictedAt: Long,
)
