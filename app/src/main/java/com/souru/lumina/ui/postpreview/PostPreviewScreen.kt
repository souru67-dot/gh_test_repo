package com.souru.lumina.ui.postpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind

private enum class PreviewTab(val label: String) {
    GRID("グリッド"),
    FEED("フィード"),
    REEL("リール"),
}

/** フィードの縦横比プリセット。 */
private enum class FeedAspect(val label: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    LANDSCAPE("1.91:1", 1.91f),
}

/**
 * Instagram投稿シミュレーション。グリッド/フィード/リールでの見え方と
 * UI被りを投稿前に確認する。オーバーレイは自前描画の近似。
 */
@UnstableApi
@Composable
fun PostPreviewScreen(
    mediaId: Long,
    onClose: () -> Unit,
    viewModel: PostPreviewViewModel = viewModel(
        key = "postPreview-$mediaId",
        factory = PostPreviewViewModel.factory(mediaId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.item

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
            }
            Text(
                text = "投稿プレビュー",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!state.loading) {
                    Text("メディアが見つかりません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        val tabs = if (item.kind == MediaKind.VIDEO) {
            listOf(PreviewTab.REEL)
        } else {
            PreviewTab.entries
        }
        var selectedTab by rememberSaveable { mutableStateOf(tabs.first().name) }
        val tab = tabs.firstOrNull { it.name == selectedTab } ?: tabs.first()

        if (tabs.size > 1) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                tabs.forEach { t ->
                    Text(
                        text = t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (t == tab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (t == tab) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                            )
                            .clickable { selectedTab = t.name }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                PreviewTab.GRID -> GridPreview(
                    item = item,
                    neighbors = state.neighbors,
                    libraryPhotos = state.libraryPhotos,
                    onToggleNeighbor = viewModel::toggleNeighbor,
                )

                PreviewTab.FEED -> FeedPreview(item = item)

                PreviewTab.REEL -> ReelPreview(item = item)
            }
        }

        Text(
            text = "実際の表示はInstagramの仕様変更により異なる場合があります",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/* ---------- グリッド(プロフィール)プレビュー ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridPreview(
    item: MediaItem,
    neighbors: List<MediaItem>,
    libraryPhotos: List<MediaItem>,
    onToggleNeighbor: (MediaItem) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 簡易プロフィールヘッダー(ダミー)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.width(20.dp))
            repeat(3) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                    )
                }
            }
        }

        // 3列グリッド(先頭セルが今回の投稿。セル比率は現行Instagramの3:4近似)
        val cells: List<MediaItem?> = listOf<MediaItem?>(item) +
            (0 until 8).map { neighbors.getOrNull(it) }
        Column {
            cells.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .aspectRatio(3f / 4f)
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            if (cell != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(
                                            MediaThumb(
                                                uri = cell.uri,
                                                id = cell.id,
                                                rotationDeg = if (cell.isRaw) cell.orientationDeg else 0,
                                            ),
                                        )
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    // グリッドサムネイルは中央クロップで切り抜かれる
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = { showPicker = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = "他のセルに自分の写真を置く(${neighbors.size}/8)",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = "並べる写真を選択(選択順に配置)",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                items(libraryPhotos, key = { it.id }) { photo ->
                    val selected = neighbors.any { it.id == photo.id }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onToggleNeighbor(photo) },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(
                                    MediaThumb(
                                        uri = photo.uri,
                                        id = photo.id,
                                        rotationDeg = if (photo.isRaw) photo.orientationDeg else 0,
                                    ),
                                )
                                .crossfade(true)
                                .build(),
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "選択中",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------- フィードプレビュー ---------- */

@Composable
private fun FeedPreview(item: MediaItem) {
    var aspect by rememberSaveable { mutableStateOf(FeedAspect.PORTRAIT.name) }
    val selected = FeedAspect.entries.firstOrNull { it.name == aspect } ?: FeedAspect.PORTRAIT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FeedAspect.entries.forEach { option ->
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (option == selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (option == selected) Color.White else Color.White.copy(alpha = 0.08f),
                        )
                        .clickable { aspect = option.name }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // フィード投稿の簡易ヘッダー
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(width = 110.dp, height = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            )
        }

        // 画像全体を表示し、選択比率で中央クロップした際に切り落とされる
        // 領域を半透明で示す
        val imageAspect = if (item.width > 0 && item.height > 0) {
            item.width.toFloat() / item.height
        } else {
            4f / 3f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageAspect),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        MediaThumb(
                            uri = item.uri,
                            id = item.id,
                            rotationDeg = if (item.isRaw) item.orientationDeg else 0,
                        ),
                    )
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            val target = selected.ratio
            if (imageAspect > target) {
                // 左右がクロップされる
                val keep = target / imageAspect
                val strip = (1f - keep) / 2f
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(strip)
                        .align(Alignment.CenterStart)
                        .background(Color.Black.copy(alpha = 0.65f)),
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(strip)
                        .align(Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.65f)),
                )
            } else if (imageAspect < target) {
                // 上下がクロップされる
                val keep = imageAspect / target
                val strip = (1f - keep) / 2f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(strip)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.65f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(strip)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.65f)),
                )
            }
        }

        // アクション行(ダミー)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Outlined.FavoriteBorder, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Icon(Icons.AutoMirrored.Outlined.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            text = "明るい部分がフィードに表示される範囲です(中央クロップ)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/* ---------- リールプレビュー ---------- */

@UnstableApi
@Composable
private fun ReelPreview(item: MediaItem) {
    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var captionLines by rememberSaveable { mutableStateOf(1) } // 1 / 2 / 5(展開)

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101010)),
            ) {
                if (item.kind == MediaKind.VIDEO) {
                    ReelVideo(item)
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(
                                MediaThumb(
                                    uri = item.uri,
                                    id = item.id,
                                    rotationDeg = if (item.isRaw) item.orientationDeg else 0,
                                ),
                            )
                            .crossfade(true)
                            .build(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (overlayVisible) {
                    ReelOverlay(captionLines = captionLines)
                }
            }
        }

        // 表示切替
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text("UIオーバーレイ", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Switch(
                checked = overlayVisible,
                onCheckedChange = { overlayVisible = it },
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            listOf(1 to "1行", 2 to "2行", 5 to "展開").forEach { (lines, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (captionLines == lines) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (captionLines == lines) Color.White else Color.White.copy(alpha = 0.08f),
                        )
                        .clickable { captionLines = lines }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun ReelVideo(item: MediaItem) {
    val context = LocalContext.current
    val player = remember(item.id) {
        // 動画編集画面で適用中のLUT・調整をそのまま再生に反映する
        val effects = (context.applicationContext as LuminaApplication)
            .container.videoEditSession.effectsFor(item.id)
        ExoPlayer.Builder(context).build().apply {
            if (effects.isNotEmpty()) setVideoEffects(effects) // prepare前に適用
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                // リールは全画面ズーム(クロップ)相当
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Instagramリール風のUIオーバーレイ(自前描画の近似)。 */
@Composable
private fun ReelOverlay(captionLines: Int) {
    Box(Modifier.fillMaxSize()) {
        // 上部: タイトルとカメラ
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "リール",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.PhotoCamera,
                null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
        }

        // 右側: アクションアイコン列
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 84.dp),
        ) {
            ReelAction(Icons.Outlined.FavoriteBorder, "119.7万")
            ReelAction(Icons.Outlined.ChatBubbleOutline, "3,632")
            ReelAction(Icons.Outlined.Repeat, "8.5万")
            ReelAction(Icons.AutoMirrored.Outlined.Send, "23.8万")
            ReelAction(Icons.Outlined.BookmarkBorder, "11.4万")
            Icon(
                Icons.Default.MoreVert,
                null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        }

        // 下部左: ユーザー名・音源・キャプション
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 12.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "your_account",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.MusicNote,
                    null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "アーティスト名 · 楽曲タイトル",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "キャプションのサンプルテキストです。ここに投稿の説明文が入り、" +
                    "長い場合は折りたたまれます。被写体やテロップがこの領域に" +
                    "重ならないか確認してください。#lumina #photography #cinematic",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = captionLines,
            )
        }

        // 下部: プログレスバー
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.35f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
