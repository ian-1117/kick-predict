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
        picks.forEach { valuePickDao.insertIfAbsent(it.toEntity()) }
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
    )
}
