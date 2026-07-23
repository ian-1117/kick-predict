package com.kickpredict.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kickpredict.data.local.dao.FixtureCacheDao
import com.kickpredict.data.local.dao.MatchResultDao
import com.kickpredict.data.local.dao.PredictionLogDao
import com.kickpredict.data.local.dao.SavedParlayDao
import com.kickpredict.data.local.dao.TeamDao
import com.kickpredict.data.local.dao.ValuePickDao
import com.kickpredict.data.local.entity.FixtureCacheEntity
import com.kickpredict.data.local.entity.MatchResultEntity
import com.kickpredict.data.local.entity.ParlayLegEntity
import com.kickpredict.data.local.entity.PredictionLogEntity
import com.kickpredict.data.local.entity.SavedParlayEntity
import com.kickpredict.data.local.entity.TeamEntity
import com.kickpredict.data.local.entity.ValuePickEntity

@Database(
    entities = [
        TeamEntity::class,
        FixtureCacheEntity::class,
        PredictionLogEntity::class,
        MatchResultEntity::class,
        ValuePickEntity::class,
        SavedParlayEntity::class,
        ParlayLegEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KickPredictDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun fixtureCacheDao(): FixtureCacheDao
    abstract fun predictionLogDao(): PredictionLogDao
    abstract fun matchResultDao(): MatchResultDao
    abstract fun valuePickDao(): ValuePickDao
    abstract fun savedParlayDao(): SavedParlayDao

    companion object {
        const val NAME = "kick_predict.db"
    }
}
