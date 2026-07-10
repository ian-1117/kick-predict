package com.kickpredict.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kickpredict.presentation.dashboard.DashboardScreen
import com.kickpredict.presentation.detail.PredictionDetailScreen
import com.kickpredict.presentation.matchlist.MatchListScreen
import com.kickpredict.presentation.simulation.SeasonSimulationScreen
import com.kickpredict.presentation.standings.StandingsScreen
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
    const val TEAM = "team/{teamId}"
    fun team(teamId: String) = "team/$teamId"
    const val ARG_TEAM_ID = "teamId"
}

@Composable
fun KickPredictNavHost(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MATCH_LIST) {
        composable(Routes.MATCH_LIST) {
            MatchListScreen(
                onMatchClick = { matchId -> navController.navigate(Routes.detail(matchId)) },
                onDashboard = { navController.navigate(Routes.DASHBOARD) },
                onStandings = { navController.navigate(Routes.STANDINGS) },
                currentTheme = currentTheme,
                onSelectTheme = onSelectTheme,
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STANDINGS) {
            StandingsScreen(
                onBack = { navController.popBackStack() },
                onTeamClick = { teamId -> navController.navigate(Routes.team(teamId)) },
                onSimulate = { navController.navigate(Routes.SIMULATION) },
            )
        }
        composable(Routes.SIMULATION) {
            SeasonSimulationScreen(onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
            )
        }
    }
}
