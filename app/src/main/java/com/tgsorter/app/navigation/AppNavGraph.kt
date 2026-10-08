package com.tgsorter.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tgsorter.app.domain.model.ReviewMode
import com.tgsorter.app.ui.screens.channels.ChannelListScreen
import com.tgsorter.app.ui.screens.home.HomeScreen
import com.tgsorter.app.ui.screens.results.ResultsScreen
import com.tgsorter.app.ui.screens.review.ReviewScreen

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onContinue = { listId -> navController.navigateSingle(Routes.review(listId)) },
                onOpenResults = { listId -> navController.navigateSingle(Routes.results(listId)) },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_MODE) {
                    type = NavType.StringType
                    defaultValue = ReviewMode.PENDING.name
                },
                navArgument(Routes.ARG_POSITION) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) {
            ReviewScreen(
                onBack = { navController.navigateBack() },
                onOpenResults = { listId -> navController.navigateSingle(Routes.results(listId)) },
                onOpenStatusList = { listId, status ->
                    navController.navigateSingle(Routes.channels(listId, status))
                },
            )
        }

        composable(
            route = Routes.RESULTS,
            arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType }),
        ) {
            ResultsScreen(
                onBack = { navController.navigateBack() },
                onOpenStatusList = { listId, status ->
                    navController.navigateSingle(Routes.channels(listId, status))
                },
                onReview = { listId, mode -> navController.navigateToReview(Routes.review(listId, mode)) },
            )
        }

        composable(
            route = Routes.CHANNELS,
            arguments = listOf(
                navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_STATUS) { type = NavType.StringType },
            ),
        ) {
            ChannelListScreen(
                onBack = { navController.navigateBack() },
                onShowInReview = { listId, position ->
                    navController.navigateToReview(Routes.review(listId, position = position))
                },
            )
        }
    }
}

/** Назад, но не дальше главного экрана (защита от двойного нажатия). */
private fun NavHostController.navigateBack() {
    if (previousBackStackEntry != null) popBackStack()
}

private fun NavHostController.navigateSingle(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** Открывает экран просмотра поверх главного, убирая промежуточные экраны из стека. */
private fun NavHostController.navigateToReview(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}
