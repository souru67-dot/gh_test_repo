package com.souru.lumina.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.souru.lumina.ui.trash.TrashScreen
import com.souru.lumina.ui.videoedit.VideoEditScreen
import com.souru.lumina.util.hasMediaAccess

object Routes {
    const val Onboarding = "onboarding"
    const val Gallery = "gallery"
    const val Trash = "trash"
    const val PhotoEdit = "photoEdit/{mediaId}"
    const val VideoEdit = "videoEdit/{mediaId}"

    fun photoEdit(mediaId: Long) = "photoEdit/$mediaId"
    fun videoEdit(mediaId: Long) = "videoEdit/$mediaId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    // startDestinationは初回コンポジションで一度だけ決める。
    // 権限付与後に動的に変えるとNavHostのグラフ差し替えとpopUpTo(inclusive)が
    // 衝突してクラッシュするため、遷移はnavigateのみで行う
    val startDestination = remember {
        if (hasMediaAccess(context)) Routes.Gallery else Routes.Onboarding
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onContinue = {
                    // 許可の結果にかかわらず一覧へ(権限チェックは一覧側で行う)
                    navController.navigate(Routes.Gallery) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Gallery) {
            GalleryRoute(
                onOpenPhotoEditor = { mediaId ->
                    navController.navigate(Routes.photoEdit(mediaId)) { launchSingleTop = true }
                },
                onOpenVideoEditor = { mediaId ->
                    navController.navigate(Routes.videoEdit(mediaId)) { launchSingleTop = true }
                },
                onOpenTrash = {
                    navController.navigate(Routes.Trash) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.Trash) {
            TrashScreen(onClose = { navController.popBackStack() })
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
        composable(
            route = Routes.VideoEdit,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
            VideoEditScreen(
                mediaId = mediaId,
                onClose = { navController.popBackStack() },
            )
        }
    }
}
