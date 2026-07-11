package com.souru.lumina.ui.favorites

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import com.souru.lumina.ui.common.LuminaLoading
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.Trash
import com.souru.lumina.util.formatDuration

/** お気に入り(IS_FAVORITE=1)のみを表示するビュー。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FavoritesScreen(
    onClose: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retryLoad()
    }

    fun toggleFavorite(entry: GalleryEntry) {
        val uris = listOfNotNull(entry.item.uri, entry.counterpart?.uri)
        favoriteLauncher.launch(
            Trash.favoriteRequest(context, uris, !entry.item.isFavorite),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "お気に入り",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LuminaLoading()
                }
            } else if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "お気に入りはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.entries, key = { _, e -> e.id }) { index, entry ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { viewerIndex = index },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(
                                        MediaThumb(
                                            uri = entry.item.uri,
                                            id = entry.item.id,
                                            rotationDeg = if (entry.item.isRaw) {
                                                entry.item.orientationDeg
                                            } else {
                                                0
                                            },
                                        ),
                                    )
                                    .crossfade(true)
                                    .build(),
                                contentDescription = entry.item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            )
                            if (entry.item.kind == MediaKind.VIDEO) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "動画",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.size(3.dp))
                                    Text(
                                        text = formatDuration(entry.item.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        viewerIndex?.let { index ->
            if (state.entries.isNotEmpty()) {
                ViewerScreen(
                    entries = state.entries,
                    initialIndex = index,
                    onClose = { viewerIndex = null },
                    onToggleFavorite = { entry -> toggleFavorite(entry) },
                )
            } else {
                viewerIndex = null
            }
        }
    }
}
