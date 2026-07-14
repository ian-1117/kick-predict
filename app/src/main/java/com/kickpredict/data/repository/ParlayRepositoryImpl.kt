package com.kickpredict.data.repository

import com.kickpredict.data.local.dao.SavedParlayDao
import com.kickpredict.data.local.entity.ParlayLegEntity
import com.kickpredict.data.local.entity.SavedParlayEntity
import com.kickpredict.domain.model.ParlayLeg
import com.kickpredict.domain.model.PredictedOutcome
import com.kickpredict.domain.model.SavedParlay
import com.kickpredict.domain.repository.ParlayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParlayRepositoryImpl(
    private val savedParlayDao: SavedParlayDao,
) : ParlayRepository {

    override suspend fun save(parlay: SavedParlay) = withContext(Dispatchers.IO) {
        savedParlayDao.insertParlay(
            SavedParlayEntity(
                id = parlay.id,
                comboOdds = parlay.comboOdds,
                edgePercent = parlay.edgePercent,
                kellyStakeFraction = parlay.kellyStakeFraction,
                createdAt = parlay.createdAt,
            ),
        )
        savedParlayDao.insertLegs(
            parlay.legs.map { leg ->
                ParlayLegEntity(
                    parlayId = parlay.id,
                    matchId = leg.matchId,
                    league = leg.league,
                    homeTeam = leg.homeTeam,
                    awayTeam = leg.awayTeam,
                    pickedOutcome = leg.pickedOutcome.name,
                    odds = leg.odds,
                )
            },
        )
    }

    override suspend fun all(): List<SavedParlay> = withContext(Dispatchers.IO) {
        val legsByParlay = savedParlayDao.getLegs().groupBy { it.parlayId }
        savedParlayDao.getParlays().map { p ->
            SavedParlay(
                id = p.id,
                legs = legsByParlay[p.id].orEmpty().map { leg ->
                    ParlayLeg(
                        matchId = leg.matchId,
                        league = leg.league,
                        homeTeam = leg.homeTeam,
                        awayTeam = leg.awayTeam,
                        pickedOutcome = runCatching { PredictedOutcome.valueOf(leg.pickedOutcome) }
                            .getOrDefault(PredictedOutcome.DRAW),
                        odds = leg.odds,
                    )
                },
                comboOdds = p.comboOdds,
                edgePercent = p.edgePercent,
                kellyStakeFraction = p.kellyStakeFraction,
                createdAt = p.createdAt,
            )
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        savedParlayDao.deleteLegs(id)
        savedParlayDao.deleteParlay(id)
    }
}
