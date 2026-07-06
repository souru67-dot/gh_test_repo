package com.souru.lumina.ui.viewer

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 動画1本のページ。表示中(settled)のページだけ ExoPlayer を生成し、
 * それ以外はサムネイルを表示する。
 */
@Composable
fun VideoPage(
    entry: GalleryEntry,
    isActive: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    containerModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = containerModifier
            .fillMaxSize()
            .pointerInput(entry.id) { detectTapGestures(onTap = { onToggleChrome() }) },
    ) {
        if (isActive) {
            ActiveVideoPlayer(entry = entry, chromeVisible = chromeVisible, onToggleChrome = onToggleChrome)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(MediaThumb(entry.item.uri, entry.item.id))
                    .crossfade(true)
                    .build(),
                contentDescription = entry.item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideoPlayer(
    entry: GalleryEntry,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(entry.item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(entry.item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        // PlayerView にタッチを奪わせないための透明レイヤー
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(entry.id) { detectTapGestures(onTap = { onToggleChrome() }) },
        )

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VideoControls(player = player)
        }
    }
}

// このファイルはmedia3用にandroidx.annotation.OptInをimportしているため、
// Kotlinコンパイラ向けのopt-inは完全修飾で指定する
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoControls(player: Player) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(player) {
        while (isActive) {
            playing = player.isPlaying
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f),
                ),
            )
            // バーの表示状態に依存しないInsetsでコントロールの位置を固定する
            .padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = {
                if (player.isPlaying) player.pause() else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            }) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "一時停止" else "再生",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Slider(
                value = if (dragFraction >= 0f) {
                    dragFraction
                } else if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    if (durationMs > 0 && dragFraction >= 0f) {
                        player.seekTo((dragFraction * durationMs).toLong())
                    }
                    dragFraction = -1f
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}
