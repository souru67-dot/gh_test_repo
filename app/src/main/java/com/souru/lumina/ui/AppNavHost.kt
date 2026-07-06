package com.souru.lumina.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.souru.lumina.ui.albums.AlbumsScreen
import com.souru.lumina.ui.favorites.FavoritesScreen
import com.souru.lumina.ui.gallery.GalleryRoute
import com.souru.lumina.ui.library.LibraryScreen
import com.souru.lumina.ui.library.LutManagerScreen
import com.souru.lumina.ui.onboarding.OnboardingScreen
import com.souru.lumina.ui.photoedit.PhotoEditScreen
import com.souru.lumina.ui.trash.TrashScreen
import com.souru.lumina.ui.videoedit.VideoEditScreen
import com.souru.lumina.util.hasMediaAccess

object Routes {
    const val Onboarding = "onboarding"
    const val Photos = "photos"
    const val Albums = "albums"
    const val Library = "library"
    const val Trash = "trash"
    const val Favorites = "favorites"
    const val LutManager = "lutManager"
    const val Album = "album/{bucketId}?name={name}"
    const val PhotoEdit = "photoEdit/{mediaId}"
    const val VideoEdit = "videoEdit/{mediaId}"

    fun album(bucketId: Long, name: String) = "album/$bucketId?name=${Uri.encode(name)}"
    fun photoEdit(mediaId: Long) = "photoEdit/$mediaId"
    fun videoEdit(mediaId: Long) = "videoEdit/$mediaId"
}

/** ボトムナビの高さぶんのコンテンツ余白(NavigationBarの標準高さ)。 */
val BottomNavContentPadding = 80.dp

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // フォトタブ内のビューア/選択モード中はボトムナビを隠す
    var photosTabBarVisible by remember { mutableStateOf(true) }

    val tabs = remember {
        listOf(
            BottomTab(Routes.Photos, "フォト", Icons.Outlined.Image),
            BottomTab(Routes.Albums, "アルバム", Icons.Outlined.Collections),
            BottomTab(Routes.Library, "ライブラリ", Icons.Outlined.LibraryBooks),
        )
    }
    val isTabRoute = tabs.any { it.route == currentRoute }
    val showBottomBar = isTabRoute && (currentRoute != Routes.Photos || photosTabBarVisible)

    // startDestinationは常にフォトに固定(動的に変えるとグラフ差し替えで
    // クラッシュするため)。権限がない初回のみオンボーディングへ遷移する
    LaunchedEffect(Unit) {
        if (!hasMediaAccess(context)) {
            navController.navigate(Routes.Onboarding) { launchSingleTop = true }
        }
    }

    fun navigateToTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Photos,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onContinue = { navController.popBackStack() },
                )
            }
            composable(Routes.Photos) {
                GalleryRoute(
                    onOpenPhotoEditor = { mediaId ->
                        navController.navigate(Routes.photoEdit(mediaId)) { launchSingleTop = true }
                    },
                    onOpenVideoEditor = { mediaId ->
                        navController.navigate(Routes.videoEdit(mediaId)) { launchSingleTop = true }
                    },
                    onBottomBarVisibleChange = { photosTabBarVisible = it },
                    bottomContentPadding = BottomNavContentPadding,
                )
            }
            composable(Routes.Albums) {
                AlbumsScreen(
                    onOpenAlbum = { album ->
                        navController.navigate(Routes.album(album.id, album.name)) {
                            launchSingleTop = true
                        }
                    },
                    bottomContentPadding = BottomNavContentPadding,
                )
            }
            composable(
                route = Routes.Album,
                arguments = listOf(
                    navArgument("bucketId") { type = NavType.LongType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val bucketId = entry.arguments?.getLong("bucketId") ?: return@composable
                val name = entry.arguments?.getString("name").orEmpty()
                GalleryRoute(
                    onOpenPhotoEditor = { mediaId ->
                        navController.navigate(Routes.photoEdit(mediaId)) { launchSingleTop = true }
                    },
                    onOpenVideoEditor = { mediaId ->
                        navController.navigate(Routes.videoEdit(mediaId)) { launchSingleTop = true }
                    },
                    bucketId = bucketId,
                    albumName = name.ifEmpty { "アルバム" },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Routes.Library) {
                LibraryScreen(
                    onOpenFavorites = {
                        navController.navigate(Routes.Favorites) { launchSingleTop = true }
                    },
                    onOpenTrash = {
                        navController.navigate(Routes.Trash) { launchSingleTop = true }
                    },
                    onOpenLutManager = {
                        navController.navigate(Routes.LutManager) { launchSingleTop = true }
                    },
                    bottomContentPadding = BottomNavContentPadding,
                )
            }
            composable(Routes.Trash) {
                TrashScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.Favorites) {
                FavoritesScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.LutManager) {
                LutManagerScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = Routes.PhotoEdit,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { entry ->
                val mediaId = entry.arguments?.getLong("mediaId") ?: return@composable
                PhotoEditScreen(
                    mediaId = mediaId,
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.VideoEdit,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { entry ->
                val mediaId = entry.arguments?.getLong("mediaId") ?: return@composable
                VideoEditScreen(
                    mediaId = mediaId,
                    onClose = { navController.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}
