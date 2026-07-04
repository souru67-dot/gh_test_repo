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
import com.souru.koyomi.ui.event.EventEditScreen
import com.souru.koyomi.ui.month.MonthScreen
import com.souru.koyomi.ui.onboarding.OnboardingScreen
import java.time.LocalDate

object Routes {
    const val ONBOARDING = "onboarding"
    const val MONTH = "month"
    const val EDITOR =
        "editor?eventId={eventId}&beginMs={beginMs}&endMs={endMs}&dateEpochDay={dateEpochDay}"

    fun editorForNew(date: LocalDate): String =
        "editor?dateEpochDay=${date.toEpochDay()}"

    fun editorForEdit(eventId: Long, beginMs: Long, endMs: Long): String =
        "editor?eventId=$eventId&beginMs=$beginMs&endMs=$endMs"
}

@Composable
fun AppNavHost() {
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
        composable(Routes.MONTH) {
            MonthScreen(
                onCreateEvent = { date ->
                    navController.navigate(Routes.editorForNew(date))
                },
                onEditEvent = { eventId, beginMs, endMs ->
                    navController.navigate(Routes.editorForEdit(eventId, beginMs, endMs))
                },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("beginMs") { type = NavType.LongType; defaultValue = -1L },
                navArgument("endMs") { type = NavType.LongType; defaultValue = -1L },
                navArgument("dateEpochDay") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            EventEditScreen(onClose = { navController.popBackStack() })
        }
    }
}
