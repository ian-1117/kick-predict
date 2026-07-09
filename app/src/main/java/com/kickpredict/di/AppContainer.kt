package com.kickpredict.di

import android.content.Context
import androidx.room.Room
import com.kickpredict.data.local.KickPredictDatabase
import com.kickpredict.data.remote.NetworkModule
import com.kickpredict.data.real.RealDataProvider
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.data.repository.MatchRepositoryImpl
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.rating.MutableEloProvider
import com.kickpredict.domain.rating.MutablePoissonProvider
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.usecase.GetCalibrationDashboardUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchUseCase
import com.kickpredict.domain.usecase.GetStandingsUseCase
import com.kickpredict.domain.usecase.GetTeamUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import com.kickpredict.domain.usecase.RecordMatchResultUseCase
import com.kickpredict.domain.usecase.SeedSampleResultsUseCase
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
    private val eloProvider = MutableEloProvider()
    private val poissonProvider = MutablePoissonProvider()

    private val engine = PredictionEngine(
        calibration = calibrationProvider,
        confidenceCalibration = confidenceCalibration,
        eloProvider = eloProvider,
        poissonProvider = poissonProvider,
    )

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Bundled real historical data (football-data.co.uk). Falls back to mock if the assets are missing.
    private val realData = RealDataProvider(context.applicationContext.assets)

    val repository: MatchRepository = MatchRepositoryImpl(
        api = NetworkModule.predictionApi(),
        json = json,
        fixtureCacheDao = database.fixtureCacheDao(),
        teamDao = database.teamDao(),
        offlineFallback = { if (realData.hasData) realData.matches() else com.kickpredict.data.mock.MockDataProvider.matches() },
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
        eloProvider = eloProvider,
        poissonProvider = poissonProvider,
        priorResults = { if (realData.hasData) realData.priorResults() else emptyList() },
        // With real data, recorded (2023-24) results drive standings/accuracy but not the models,
        // so the displayed predictions stay out-of-sample against those seasons.
        trainModelsOnRecordedResults = !realData.hasData,
    )
    val getCalibrationDashboard = GetCalibrationDashboardUseCase(calibrationRepository, recalibrate)
    val getStandings = GetStandingsUseCase(calibrationRepository)
    val getTeam = GetTeamUseCase(getPredictedMatches, getStandings, calibrationRepository, eloProvider)
    val seedSampleResults = SeedSampleResultsUseCase(getPredictedMatches, calibrationRepository)

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        applicationScope.launch {
            runCatching {
                // Pre-train the models (on prior real seasons) so predictions are ready.
                recalibrate()
                // First launch with real data: seed the real display-season results so standings,
                // the accuracy dashboard and team pages are populated with real football. Predictions
                // are logged first (out-of-sample) and then scored against the real outcomes.
                if (realData.hasData && calibrationRepository.recordedResultCount() == 0) {
                    val matches = getPredictedMatches()
                    val results = realData.displayResults()
                    matches.forEach { m ->
                        results[m.id]?.let { (home, away) -> calibrationRepository.recordResult(m.id, home, away) }
                    }
                    recalibrate() // refit confidence/league calibration against the real outcomes
                }
            }
        }
    }
}
