package com.kickpredict.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kickpredict.presentation.detail.PredictionDetailScreen
import com.kickpredict.presentation.matchlist.MatchListScreen

object Routes {
    const val MATCH_LIST = "matches"
    const val MATCH_DETAIL = "matches/{matchId}"
    fun detail(matchId: String) = "matches/$matchId"
    const val ARG_MATCH_ID = "matchId"
}

@Composable
fun KickPredictNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MATCH_LIST) {
        composable(Routes.MATCH_LIST) {
            MatchListScreen(
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
