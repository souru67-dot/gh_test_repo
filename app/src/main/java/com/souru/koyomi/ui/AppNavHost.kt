package com.souru.koyomi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.ui.anniversary.AnniversaryScreen
import com.souru.koyomi.ui.event.EventEditScreen
import com.souru.koyomi.ui.month.MonthScreen
import com.souru.koyomi.ui.onboarding.OnboardingScreen
import com.souru.koyomi.ui.search.SearchScreen
import com.souru.koyomi.ui.settings.SettingsScreen
import com.souru.koyomi.ui.tasks.TasksScreen
import com.souru.koyomi.ui.timeline.TimelineScreen
import com.souru.koyomi.ui.year.YearScreen
import java.time.LocalDate

object Routes {
    const val ONBOARDING = "onboarding"
    const val MONTH = "month"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val TASKS = "tasks"
    const val YEAR = "year"
    const val ANNIVERSARIES = "anniversaries"
    const val EDITOR =
        "editor?eventId={eventId}&beginMs={beginMs}&endMs={endMs}" +
            "&dateEpochDay={dateEpochDay}&taskId={taskId}"
    const val TIMELINE = "timeline/{mode}?epochDay={epochDay}"

    fun editorForNew(date: LocalDate): String =
        "editor?dateEpochDay=${date.toEpochDay()}"

    fun editorForEdit(eventId: Long, beginMs: Long, endMs: Long): String =
        "editor?eventId=$eventId&beginMs=$beginMs&endMs=$endMs"

    fun editorForTask(taskId: Long): String = "editor?taskId=$taskId"

    fun timeline(mode: String, date: LocalDate): String =
        "timeline/$mode?epochDay=${date.toEpochDay()}"
}

/** SavedStateHandle key: a date pick (epoch day) waiting for the month view. */
private const val KEY_JUMP_EPOCH_DAY = "jump_epoch_day"

@Composable
fun AppNavHost(
    deepLinkEpochDay: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as KoyomiApplication }

    val onboardingDone by app.container.settingsRepository.onboardingDone
        .collectAsStateWithLifecycle(initialValue = null)

    // Wait for DataStore before choosing a start destination to avoid a flash.
    val done = onboardingDone ?: return
    // Decide once; later preference changes must not rebuild the nav graph.
    val startDestination = remember {
        if (!done && !app.container.calendarRepository.hasReadPermission()) {
            Routes.ONBOARDING
        } else {
            Routes.MONTH
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.MONTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MONTH) { entry ->
            // The year view reports its pick through the month entry's
            // SavedStateHandle; it rides the same jump path as widget taps.
            val yearPick by entry.savedStateHandle
                .getStateFlow<Long?>(KEY_JUMP_EPOCH_DAY, null)
                .collectAsStateWithLifecycle()
            MonthScreen(
                onCreateEvent = { date ->
                    navController.navigate(Routes.editorForNew(date))
                },
                onEditEvent = { eventId, beginMs, endMs ->
                    navController.navigate(Routes.editorForEdit(eventId, beginMs, endMs))
                },
                onEditTask = { taskId ->
                    navController.navigate(Routes.editorForTask(taskId))
                },
                onOpenTimeline = { mode, date ->
                    navController.navigate(Routes.timeline(mode, date))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenYear = { navController.navigate(Routes.YEAR) },
                onOpenAnniversaries = { navController.navigate(Routes.ANNIVERSARIES) },
                deepLinkEpochDay = deepLinkEpochDay ?: yearPick,
                onDeepLinkConsumed = {
                    onDeepLinkConsumed()
                    entry.savedStateHandle[KEY_JUMP_EPOCH_DAY] = null
                },
            )
        }
        composable(Routes.YEAR) {
            val useJapaneseEra by app.container.settingsRepository.useJapaneseEra
                .collectAsStateWithLifecycle(initialValue = false)
            YearScreen(
                onBack = { navController.popIfCurrent(Routes.YEAR) },
                onOpenMonth = { date ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(KEY_JUMP_EPOCH_DAY, date.toEpochDay())
                    navController.popIfCurrent(Routes.YEAR)
                },
                useJapaneseEra = useJapaneseEra,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popIfCurrent(Routes.SEARCH) },
                onOpenEvent = { eventId, beginMs, endMs ->
                    navController.navigate(Routes.editorForEdit(eventId, beginMs, endMs))
                },
            )
        }
        composable(
            route = Routes.TIMELINE,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("epochDay") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { backStackEntry ->
            TimelineScreen(
                mode = backStackEntry.arguments?.getString("mode") ?: "week",
                onBack = { navController.popIfCurrent(Routes.TIMELINE) },
                onEditEvent = { eventId, beginMs, endMs ->
                    navController.navigate(Routes.editorForEdit(eventId, beginMs, endMs))
                },
            )
        }
        composable(Routes.ANNIVERSARIES) {
            AnniversaryScreen(onBack = { navController.popIfCurrent(Routes.ANNIVERSARIES) })
        }
        composable(Routes.TASKS) {
            TasksScreen(
                onBack = { navController.popIfCurrent(Routes.TASKS) },
                onEditTask = { taskId ->
                    navController.navigate(Routes.editorForTask(taskId))
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popIfCurrent(Routes.SETTINGS) })
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("beginMs") { type = NavType.LongType; defaultValue = -1L },
                navArgument("endMs") { type = NavType.LongType; defaultValue = -1L },
                navArgument("dateEpochDay") { type = NavType.LongType; defaultValue = -1L },
                navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            EventEditScreen(onClose = { navController.popIfCurrent(Routes.EDITOR) })
        }
    }
}

/**
 * Pops only while [route] is on top. Back handlers can fire twice (double
 * tap, or a save-completion callback racing a manual close); an unguarded
 * second popBackStack() would pop the month screen too and leave an empty,
 * blank NavHost.
 */
private fun androidx.navigation.NavHostController.popIfCurrent(route: String) {
    if (currentDestination?.route == route) popBackStack()
}
