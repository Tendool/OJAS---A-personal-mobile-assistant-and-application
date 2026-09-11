package com.ojas.assistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.ojas.assistant.di.AppContainer

/** Provides the dependency graph to every composable without threading it manually. */
val LocalOjas = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided. Wrap the tree in CompositionLocalProvider(LocalOjas ...).")
}

object Routes {
    const val HOME = "home"
    const val WATER = "water"
    const val WORKOUT = "workout"
    const val WORKOUT_SESSION = "workout/session"
    const val WORKOUT_EDIT = "workout/edit"
    const val MEDITATION = "meditation"
    const val SCHEDULE = "schedule"
    const val SCREEN_TIME = "screentime"
    const val SETTINGS = "settings"

    fun workoutSession(id: Long) = "$WORKOUT_SESSION/$id"
    fun workoutEdit(id: Long) = "$WORKOUT_EDIT/$id"
    fun schedule(tab: Int) = "$SCHEDULE?tab=$tab"

    /** Maps the shorthand a notification carries onto a real navigation route. */
    fun fromNotification(key: String?): String? = when (key) {
        "water" -> WATER
        "alarms" -> schedule(TAB_ALARMS)
        "reminders" -> schedule(TAB_REMINDERS)
        "calendar" -> schedule(TAB_CALENDAR)
        "screentime" -> SCREEN_TIME
        "workout" -> WORKOUT
        "meditation" -> MEDITATION
        else -> null
    }

    const val TAB_ALARMS = 0
    const val TAB_REMINDERS = 1
    const val TAB_CALENDAR = 2
}

data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomDestinations: List<NavDestination> = listOf(
    NavDestination(Routes.HOME, "Orbit", Icons.Rounded.AutoAwesome),
    NavDestination(Routes.WATER, "Water", Icons.Rounded.WaterDrop),
    NavDestination(Routes.WORKOUT, "Move", Icons.Outlined.Whatshot),
    NavDestination(Routes.MEDITATION, "Calm", Icons.Outlined.SelfImprovement),
    NavDestination(Routes.SCHEDULE, "Plan", Icons.Outlined.CalendarMonth)
)
