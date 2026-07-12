package com.kickpredict.di

import android.content.Context
import androidx.room.Room
import com.kickpredict.BuildConfig
import com.kickpredict.data.local.KickPredictDatabase
import com.kickpredict.data.remote.NetworkModule
import com.kickpredict.data.remote.live.LiveFixtureRemoteSource
import com.kickpredict.data.real.RealDataProvider
import com.kickpredict.domain.model.Match
import com.kickpredict.data.repository.CalibrationRepositoryImpl
import com.kickpredict.data.repository.MatchRepositoryImpl
import com.kickpredict.data.repository.ValuePickRepositoryImpl
import com.kickpredict.domain.calibration.MutableCalibrationProvider
import com.kickpredict.domain.calibration.MutableConfidenceCalibration
import com.kickpredict.domain.engine.PredictionEngine
import com.kickpredict.domain.rating.MutableEloProvider
import com.kickpredict.domain.rating.MutablePoissonProvider
import com.kickpredict.domain.repository.CalibrationRepository
import com.kickpredict.domain.repository.MatchRepository
import com.kickpredict.domain.simulation.SeasonSimulator
import com.kickpredict.domain.usecase.GetCalibrationDashboardUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchUseCase
import com.kickpredict.domain.usecase.GetStandingsUseCase
import com.kickpredict.domain.usecase.GetTeamUseCase
import com.kickpredict.domain.usecase.GetPendingNotificationsUseCase
import com.kickpredict.domain.usecase.GetPredictedMatchesUseCase
import com.kickpredict.domain.usecase.GetValuePicksUseCase
import com.kickpredict.domain.usecase.RecalibrateUseCase
import com.kickpredict.notifications.MatchNotifier
import com.kickpredict.notifications.NotificationLog
import com.kickpredict.notifications.NotificationPreference
import com.kickpredict.notifications.NotificationScheduler
import com.kickpredict.domain.usecase.RecordMatchResultUseCase
import com.kickpredict.domain.usecase.SeedSampleResultsUseCase
import com.kickpredict.domain.usecase.SimulateSeasonUseCase
import com.kickpredict.domain.usecase.SyncResultsUseCase
import com.kickpredict.presentation.locale.LanguagePreference
import com.kickpredict.presentation.theme.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight manual dependency-injection container. Constructed once in
 * [com.kickpredict.KickPredictApplication] and reachable from ViewModels via their factories.
 *
 * Kept manual (rather than Hilt) so the graph is explicit and the module has no annotation-
 * processing coupling beyond Room; it can be replaced by Hilt/Koin later without API changes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Which palette the app paints with; read synchronously so the first frame is already right. */
    val themePreference = ThemePreference(context)

    /** In-app language override; read synchronously so the app opens in the chosen language. */
    val languagePreference = LanguagePreference(context)

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

    // Bundled real historical data (football-data.co.uk). Falls back to mock if the assets are missing.
    private val realData = RealDataProvider(context.applicationContext.assets)
    private val bundledMatches: List<Match> by lazy {
        if (realData.hasData) realData.matches() else com.kickpredict.data.mock.MockDataProvider.matches()
    }

    // Live current-season fixtures: Europe via football-data.org, K League via API-Football. Keys
    // come from BuildConfig (local.properties, git-ignored); an absent key makes that league fall
    // back to the bundled historical data, so the app works with zero, one, or both keys set.
    private val liveSource = LiveFixtureRemoteSource(
        footballData = NetworkModule.footballDataApi(BuildConfig.FOOTBALL_DATA_KEY),
        apiFootball = NetworkModule.apiFootballApi(BuildConfig.APIFOOTBALL_KEY),
        footballDataKey = BuildConfig.FOOTBALL_DATA_KEY,
        apiFootballKey = BuildConfig.APIFOOTBALL_KEY,
        bundledByLeague = { league -> bundledMatches.filter { it.league == league } },
    )

    /** Scores of matches currently in play (from the last live fetch), for the LIVE badge on cards. */
    val liveScores: () -> Map<String, com.kickpredict.domain.model.LiveScore> = { liveSource.lastLiveScores() }

    /** Market 1X2 odds for upcoming matches (from the last live fetch), for value picks. */
    val odds: () -> Map<String, com.kickpredict.domain.model.MarketOdds> = { liveSource.lastOdds() }

    val repository: MatchRepository = MatchRepositoryImpl(
        liveSource = liveSource,
        teamDao = database.teamDao(),
        fixtureCacheDao = database.fixtureCacheDao(),
        offlineFallback = { bundledMatches },
        // The bundled as-of view rebuilds historical profiles round-by-round to stay out-of-sample.
        // With live data the current season is already a point-in-time snapshot, so the season
        // projection should simulate the *live* remaining fixtures (getMatchesAsOf falls back to the
        // live matches filtered by round) rather than a bundled season.
        asOfFallback = if (!liveSource.isConfigured && realData.hasData) { round -> realData.matchesAsOf(round) } else null,
    )

    val calibrationRepository: CalibrationRepository = CalibrationRepositoryImpl(
        predictionLogDao = database.predictionLogDao(),
        matchResultDao = database.matchResultDao(),
    )

    private val valuePickRepository = ValuePickRepositoryImpl(database.valuePickDao())

    /** Value-pick ledger + ROI: logs flagged picks at their flag-time price and settles them. */
    val getValuePicks = GetValuePicksUseCase(valuePickRepository, calibrationRepository)

    // --- Match notifications ----------------------------------------------------------------
    /** Opt-in toggle for background match notifications (default off). */
    val notificationPreference = NotificationPreference(appContext)

    /** Fire-once log so each notification event posts at most once. */
    val notificationLog = NotificationLog(appContext)

    /** Builds + posts the system notifications. */
    val matchNotifier = MatchNotifier(appContext)

    /** Scans fixtures + live state for notify-worthy events (kickoff / live / result). */
    val getPendingNotifications = GetPendingNotificationsUseCase(
        liveScores = liveScores,
        odds = odds,
        calibrationRepository = calibrationRepository,
    )

    /**
     * Turn notifications on/off. Enabling primes the log (marks every current candidate as seen, so
     * the backlog doesn't all fire at once) and schedules the periodic scan; disabling cancels it.
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreference.setEnabled(enabled)
        if (enabled) {
            applicationScope.launch {
                runCatching {
                    val matches = getPredictedMatches()
                    val pending = getPendingNotifications(matches, System.currentTimeMillis())
                    notificationLog.markNotified(pending.map { it.key })
                }
                NotificationScheduler.schedule(appContext)
            }
        } else {
            NotificationScheduler.cancel(appContext)
            notificationLog.clear()
        }
    }

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
    // Seeds the current season's real scores into the results store (live where available, else bundled).
    val syncResults = SyncResultsUseCase(
        liveResults = { liveSource.lastResults() },
        bundledResults = { if (realData.hasData) realData.displayResults() else emptyMap() },
        liveConfigured = liveSource.isConfigured,
        calibrationRepository = calibrationRepository,
    )
    val seedSampleResults = SeedSampleResultsUseCase(getPredictedMatches, calibrationRepository)
    // Reads the live calibration, so a simulated season uses the same draw inflation the engine does.
    val simulateSeason = SimulateSeasonUseCase(
        repository = repository,
        engine = engine,
        calibrationRepository = calibrationRepository,
        simulator = SeasonSimulator(calibrationProvider),
    )

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        applicationScope.launch {
            runCatching {
                // Pre-train the models (on prior seasons) so predictions are ready.
                recalibrate()
                // Load + predict the current fixtures (this populates the live source's finished-match
                // scores), then seed those results so standings, the accuracy dashboard and team pages
                // reflect the current season. Predictions are logged first, then scored against the
                // real outcomes. Live results replace any earlier import so nothing goes stale.
                val matches = getPredictedMatches()
                syncResults()
                recalibrate() // refit confidence/league calibration against the real outcomes
                // Log any currently-flagged value picks so the ROI ledger fills even before the hub
                // is opened, capturing today's odds while they're still in the live window.
                getValuePicks.record(matches, odds())
                // Re-arm the periodic notification scan if the user left notifications on.
                if (notificationPreference.enabled.value) NotificationScheduler.schedule(appContext)
            }
        }
    }
}
