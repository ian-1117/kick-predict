package com.kickpredict.data.repository

import com.kickpredict.data.local.dao.ValuePickDao
import com.kickpredict.data.local.entity.ValuePickEntity
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.ValuePick
import com.kickpredict.domain.repository.ValuePickRepository

class ValuePickRepositoryImpl(
    private val valuePickDao: ValuePickDao,
) : ValuePickRepository {

    override suspend fun record(picks: List<ValuePick>) {
        picks.forEach { pick ->
            // Lock the flag-time price on first sighting, then always advance the closing line to the
            // currently-observed price so CLV reflects how the market moved after we took our price.
            valuePickDao.insertIfAbsent(pick.toEntity())
            valuePickDao.updateClosing(pick.matchId, pick.odds)
        }
    }

    override suspend fun all(): List<ValuePick> = valuePickDao.getAll().map { it.toDomain() }

    override suspend fun clear() = valuePickDao.deleteAll()

    private fun ValuePick.toEntity() = ValuePickEntity(
        matchId = matchId,
        league = league,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        round = round,
        pickedOutcome = pickedOutcome.name,
        edge = edge,
        odds = odds,
        closingOdds = odds,
        kickoffEpochMillis = kickoffEpochMillis,
        createdAt = System.currentTimeMillis(),
    )

    private fun ValuePickEntity.toDomain() = ValuePick(
        matchId = matchId,
        league = league,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        round = round,
        pickedOutcome = PredictedOutcome.valueOf(pickedOutcome),
        edge = edge,
        odds = odds,
        kickoffEpochMillis = kickoffEpochMillis,
        closingOdds = closingOdds,
    )
}
