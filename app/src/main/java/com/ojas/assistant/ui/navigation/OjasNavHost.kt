package com.ojas.assistant.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ojas.assistant.ui.galaxy.GalaxyBackground
import com.ojas.assistant.ui.screens.CalmScreen
import com.ojas.assistant.ui.screens.HomeScreen
import com.ojas.assistant.ui.screens.ScheduleScreen
import com.ojas.assistant.ui.screens.ScreenTimeScreen
import com.ojas.assistant.ui.screens.SettingsScreen
import com.ojas.assistant.ui.screens.WaterScreen
import com.ojas.assistant.ui.screens.WorkoutEditScreen
import com.ojas.assistant.ui.screens.WorkoutListScreen
import com.ojas.assistant.ui.screens.WorkoutSessionScreen
import com.ojas.assistant.ui.viewmodel.SettingsUiState

/**
 * One galaxy, one scaffold, one nav graph.
 *
 * The GL surface is hoisted above the NavHost so it survives every navigation: screens
 * fade over a continuously running backdrop rather than each one paying to spin up its
 * own renderer.
 */
@Composable
fun OjasNavHost(
    navController: NavHostController,
    settings: SettingsUiState,
    modifier: Modifier = Modifier
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Dragging the galaxy is reserved for the home screen; everywhere else the gesture
    // belongs to the list underneath.
    val galaxyInteractive = remember(currentRoute, settings.settings.galaxyInteractive) {
        settings.settings.galaxyInteractive && currentRoute == Routes.HOME
    }

    Box(modifier.fillMaxSize()) {
        GalaxyBackground(
            modifier = Modifier.fillMaxSize(),
            starCount = settings.settings.starCount,
            reduceMotion = settings.settings.reduceMotion,
            interactive = galaxyInteractive
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.Unspecified,
            bottomBar = { OjasBottomBar(navController = navController, currentRoute = currentRoute) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.99f) }
            ) {
                ojasGraph(navController, padding)
            }
        }
    }
}

private fun NavGraphBuilder.ojasGraph(
    navController: NavHostController,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    composable(Routes.HOME) {
        HomeScreen(
            contentPadding = padding,
            onNavigate = { route -> navController.navigateTop(route) },
            onOpenWorkout = { id -> navController.navigate(Routes.workoutSession(id)) }
        )
    }

    composable(Routes.WATER) {
        WaterScreen(contentPadding = padding)
    }

    composable(Routes.WORKOUT) {
        WorkoutListScreen(
            contentPadding = padding,
            onStart = { id -> navController.navigate(Routes.workoutSession(id)) },
            onEdit = { id -> navController.navigate(Routes.workoutEdit(id)) }
        )
    }

    composable(
        route = "${Routes.WORKOUT_SESSION}/{workoutId}",
        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
    ) { entry ->
        WorkoutSessionScreen(
            workoutId = entry.arguments?.getLong("workoutId") ?: 0L,
            contentPadding = padding,
            onDone = { navController.popBackStack() }
        )
    }

    composable(
        route = "${Routes.WORKOUT_EDIT}/{workoutId}",
        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
    ) { entry ->
        WorkoutEditScreen(
            workoutId = entry.arguments?.getLong("workoutId") ?: 0L,
            contentPadding = padding,
            onDone = { navController.popBackStack() }
        )
    }

    composable(Routes.MEDITATION) {
        CalmScreen(contentPadding = padding)
    }

    composable(
        route = "${Routes.SCHEDULE}?tab={tab}",
        arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
    ) { entry ->
        ScheduleScreen(
            initialTab = entry.arguments?.getInt("tab") ?: 0,
            contentPadding = padding
        )
    }

    composable(Routes.SCREEN_TIME) {
        ScreenTimeScreen(contentPadding = padding)
    }

    composable(Routes.SETTINGS) {
        SettingsScreen(contentPadding = padding)
    }
}

/** Tab-style navigation: single instance, state preserved, no back stack pile-up. */
fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
