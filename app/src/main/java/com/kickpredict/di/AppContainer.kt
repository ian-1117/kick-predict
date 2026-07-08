package com.kickpredict.di

import android.content.Context
import androidx.room.Room
import com.kickpredict.data.local.KickPredictDatabase
import com.kickpredict.data.remote.NetworkModule
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.data.repository.MatchRepositoryImpl
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.usecase.GetCalibrationDashboardUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import com.kickpredict.domain.usecase.RecordMatchResultUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Lightweight manual dependency-injection container. Constructed once in
 * [com.kickpredict.KickPredictApplication] and reachable from ViewModels via their factories.
 *
 * Kept manual (rather than Hilt) so the graph is explicit and the module has no annotation-
 * processing coupling beyond Room; it can be replaced by Hilt/Koin later without API changes.
 */
class AppContainer(context: Context) {

    private val database: KickPredictDatabase = Room.databaseBuilder(
        context.applicationContext,
        KickPredictDatabase::class.java,
        KickPredictDatabase.NAME,
    ).fallbackToDestructiveMigration().build()

    // Live calibration holders — refit from accumulated results and read by the engine each call.
    private val confidenceCalibration = MutableConfidenceCalibration()
    private val calibrationProvider = MutableCalibrationProvider()

    private val engine = PredictionEngine(
        calibration = calibrationProvider,
        confidenceCalibration = confidenceCalibration,
    )

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val repository: MatchRepository = MatchRepositoryImpl(
        api = NetworkModule.predictionApi(),
        json = json,
        fixtureCacheDao = database.fixtureCacheDao(),
        teamDao = database.teamDao(),
    )

    val calibrationRepository: CalibrationRepository = CalibrationRepositoryImpl(
        predictionLogDao = database.predictionLogDao(),
        matchResultDao = database.matchResultDao(),
    )

    val getPredictedMatches = GetPredictedMatchesUseCase(repository, engine, calibrationRepository)
    val getPredictedMatch = GetPredictedMatchUseCase(repository, engine, calibrationRepository)
    val recordMatchResult = RecordMatchResultUseCase(calibrationRepository)
    // Production thresholds are the RecalibrateUseCase defaults (20 / 10). For a quick demo, pass
    // minConfidenceSamples = 3, minLeagueSamples = 3 so calibration applies after only a few results.
    val recalibrate = RecalibrateUseCase(
        calibrationRepository = calibrationRepository,
        confidenceCalibration = confidenceCalibration,
        calibrationProvider = calibrationProvider,
    )
    val getCalibrationDashboard = GetCalibrationDashboardUseCase(calibrationRepository, recalibrate)

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Apply any calibration learned from previously recorded results on startup.
        applicationScope.launch { runCatching { recalibrate() } }
    }
}
