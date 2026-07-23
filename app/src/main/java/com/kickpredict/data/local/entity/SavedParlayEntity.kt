package com.kickpredict.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kickpredict.domain.model.LeagueType

/**
 * A parlay the user saved from the accumulator builder — the header row. Its legs live in
 * [ParlayLegEntity]; the combined price / edge / stake are snapshotted here at save time so the
 * saved bet reads exactly as it did when placed. Settlement (won / lost / pending) is derived by
 * joining the legs against recorded results, so it isn't stored.
 */
@Entity(tableName = "saved_parlay")
data class SavedParlayEntity(
    @PrimaryKey val id: String,
    val comboOdds: Double,
    val edgePercent: Int,
    val kellyStakeFraction: Double,
    val createdAt: Long,
)

/** One leg of a saved parlay, self-contained so the bet still reads after the picks ledger is cleared. */
@Entity(tableName = "parlay_leg", primaryKeys = ["parlayId", "matchId"])
data class ParlayLegEntity(
    val parlayId: String,
    val matchId: String,
    val league: LeagueType,
    val homeTeam: String,
    val awayTeam: String,
    val pickedOutcome: String, // PredictedOutcome.name
    val odds: Double,
)
