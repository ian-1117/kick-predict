package com.kickpredict.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kickpredict.presentation.backtest.BacktestScreen
import com.kickpredict.presentation.compare.CompareScreen
import com.kickpredict.presentation.dashboard.DashboardScreen
import com.kickpredict.presentation.detail.PredictionDetailScreen
import com.kickpredict.presentation.matchlist.MatchListScreen
import com.kickpredict.presentation.settings.SettingsScreen
import com.kickpredict.presentation.simulation.SeasonSimulationScreen
import com.kickpredict.presentation.standings.StandingsScreen
import com.kickpredict.presentation.valuepicks.ValuePicksScreen
import com.kickpredict.presentation.locale.AppLanguage
import com.kickpredict.presentation.team.TeamScreen
import com.kickpredict.presentation.theme.AppTheme

object Routes {
    const val MATCH_LIST = "matches"
    const val MATCH_DETAIL = "matches/{matchId}"
    fun detail(matchId: String) = "matches/$matchId"
    const val ARG_MATCH_ID = "matchId"
    const val DASHBOARD = "dashboard"
    const val STANDINGS = "standings"
    const val SIMULATION = "simulation"
    const val VALUE_PICKS = "value_picks"
    const val BACKTEST = "backtest"
    const val COMPARE = "compare"
    const val SETTINGS = "settings"
    const val TEAM = "team/{teamId}"
    fun team(teamId: String) = "team/$teamId"
    const val ARG_TEAM_ID = "teamId"
}

@Composable
fun KickPredictNavHost(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // The Activity for showing the interstitial. LocalContext is replaced by a non-Activity config
    // context under the in-app language switch, so resolve the Activity from the hosting View instead.
    val view = LocalView.current
    // Keep an interstitial preloaded (once the SDK is ready) so it shows instantly at the next break.
    val adsReady = com.kickpredict.presentation.ads.AdsState.initialized
    LaunchedEffect(adsReady) {
        if (com.kickpredict.Features.ADS && adsReady) com.kickpredict.presentation.ads.InterstitialAds.preload(context)
    }
    NavHost(navController = navController, startDestination = Routes.MATCH_LIST) {
        composable(Routes.MATCH_LIST) {
            MatchListScreen(
                onMatchClick = { matchId -> navController.navigate(Routes.detail(matchId)) },
                onTeamClick = { teamId -> navController.navigate(Routes.team(teamId)) },
                onDashboard = { navController.navigate(Routes.DASHBOARD) },
                onStandings = { navController.navigate(Routes.STANDINGS) },
                onValuePicks = { navController.navigate(Routes.VALUE_PICKS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentTheme = currentTheme,
                onSelectTheme = onSelectTheme,
                currentLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onBack = { navController.popBackStack() },
                onBacktest = { navController.navigate(Routes.BACKTEST) },
            )
        }
        composable(Routes.BACKTEST) {
            BacktestScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.COMPARE) {
            CompareScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STANDINGS) {
            StandingsScreen(
                onBack = { navController.popBackStack() },
                onTeamClick = { teamId -> navController.navigate(Routes.team(teamId)) },
                onSimulate = { navController.navigate(Routes.SIMULATION) },
                onCompare = { navController.navigate(Routes.COMPARE) },
            )
        }
        composable(Routes.SIMULATION) {
            SeasonSimulationScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.VALUE_PICKS) {
            ValuePicksScreen(
                onBack = { navController.popBackStack() },
                onMatchClick = { matchId -> navController.navigate(Routes.detail(matchId)) },
            )
        }
        composable(
            route = Routes.TEAM,
            arguments = listOf(navArgument(Routes.ARG_TEAM_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val teamId = backStackEntry.arguments?.getString(Routes.ARG_TEAM_ID).orEmpty()
            TeamScreen(
                teamId = teamId,
                onBack = { navController.popBackStack() },
                onMatchClick = { matchId -> navController.navigate(Routes.detail(matchId)) },
            )
        }
        composable(
            route = Routes.MATCH_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_MATCH_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString(Routes.ARG_MATCH_ID).orEmpty()
            PredictionDetailScreen(
                matchId = matchId,
                onBack = {
                    val leaving = navController.popBackStack()
                    // Only when we actually returned to the list (staying in the app), never on exit.
                    if (leaving && com.kickpredict.Features.ADS) {
                        with(com.kickpredict.presentation.ads.InterstitialAds) {
                            view.context.findActivity()?.let { onDetailClosed(it) }
                        }
                    }
                },
            )
        }
    }
}
