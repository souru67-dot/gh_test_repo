package com.souru.lumina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.souru.lumina.ui.gallery.GalleryRoute
import com.souru.lumina.ui.onboarding.OnboardingScreen
import com.souru.lumina.ui.photoedit.PhotoEditScreen
import com.souru.lumina.util.hasMediaAccess

object Routes {
    const val Onboarding = "onboarding"
    const val Gallery = "gallery"
    const val PhotoEdit = "photoEdit/{mediaId}"

    fun photoEdit(mediaId: Long) = "photoEdit/$mediaId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasMediaAccess(context)) }

    NavHost(
        navController = navController,
        startDestination = if (permitted) Routes.Gallery else Routes.Onboarding,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onGranted = {
                    permitted = true
                    navController.navigate(Routes.Gallery) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Gallery) {
            GalleryRoute(
                onOpenPhotoEditor = { mediaId ->
                    navController.navigate(Routes.photoEdit(mediaId))
                },
            )
        }
        composable(
            route = Routes.PhotoEdit,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            PhotoEditScreen(
                mediaId = mediaId,
                onClose = { navController.popBackStack() },
            )
        }
    }
}
