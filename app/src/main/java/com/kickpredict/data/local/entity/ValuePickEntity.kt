package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kickpredict.domain.model.LeagueType

/**
 * A value pick logged the moment the model first flagged it — the price is captured at that instant
 * and never overwritten, so the ROI ledger reflects the odds you could actually have taken. The live
 * odds map only covers a short upcoming window, so without this row a settled pick's price would be
 * gone by the time its result lands.
 */
@Entity(tableName = "value_pick")
data class ValuePickEntity(
    @PrimaryKey val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val round: Int,
    /** [com.kickpredict.domain.model.PredictedOutcome] name — the outcome the pick backs. */
    val pickedOutcome: String,
    /** Model probability minus market implied probability, whole percent, at flag time. */
    val edge: Int,
    /** Decimal odds for the backed outcome, captured at flag time. */
    val odds: Double,
    val kickoffEpochMillis: Long,
    val createdAt: Long,
)
