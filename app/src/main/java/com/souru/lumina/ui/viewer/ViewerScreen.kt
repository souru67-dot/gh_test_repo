package com.souru.lumina.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.souru.lumina.ui.gallery.sharedMediaKey
import com.souru.lumina.data.MediaInfo
import com.souru.lumina.data.MediaInfoLoader
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.ui.share.ShareOptionsDialog
import com.souru.lumina.util.formatViewerTitle

/**
 * 没入型ビューア。横スワイプで前後のメディアへ、タップでUI表示切替、
 * 下スワイプ/戻る操作で閉じる。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ViewerScreen(
    entries: List<GalleryEntry>,
    initialIndex: Int,
    onClose: () -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
    onFocusedIdChange: (Long) -> Unit = {},
    // nullのアクションは下部バーに表示しない(外部URI表示やお気に入り一覧など、
    // その操作を提供できないコンテキスト向け)
    onEditPhoto: ((MediaItem) -> Unit)? = null,
    onEditVideo: ((MediaItem) -> Unit)? = null,
    onSendToLightroom: ((GalleryEntry) -> Unit)? = null,
    onDelete: ((GalleryEntry) -> Unit)? = null,
    onToggleFavorite: ((GalleryEntry) -> Unit)? = null,
    onOpenPostPreview: ((MediaItem) -> Unit)? = null,
) {
    if (entries.isEmpty()) {
        onClose()
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, entries.size - 1),
    ) { entries.size }

    var chromeVisible by remember { mutableStateOf(true) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<MediaItem?>(null) }

    // 情報シート用メタデータのプリフェッチ(表示前にIOで読んでおく)
    val context = LocalContext.current
    val infoCache = remember { mutableStateMapOf<Long, MediaInfo>() }
    LaunchedEffect(pagerState.currentPage, entries) {
        val entry = entries.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (!infoCache.containsKey(entry.id)) {
            runCatching { MediaInfoLoader.load(context, entry) }
                .onSuccess { infoCache[entry.id] = it }
        }
    }

    // UI非表示時はシステムバーも隠して完全没入にする。
    // 制御はWindowInsetsControllerCompatに一本化し、edge-to-edgeや
    // レイアウト構造には触れない(バーのhide/showのみ)
    val view = LocalView.current

    fun showSystemBars() {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 閉じるすべての経路(戻る・下スワイプ・←ボタン)で、閉じる前に
    // バー表示を復帰させる。onDisposeでの復帰はトランジション完了後に
    // なるため、グリッドがinset=0でレイアウトされて位置がずれたまま
    // 残る(LazyGridはスクロール基準をアイテムに固定する)のを防ぐ
    fun closeWithBarsRestored() {
        showSystemBars()
        onClose()
    }

    BackHandler { closeWithBarsRestored() }

    // ページ切替を親に伝え、共有要素の戻り先セルを追従させる
    LaunchedEffect(pagerState.currentPage) {
        entries.getOrNull(pagerState.currentPage)?.let { onFocusedIdChange(it.id) }
    }

    DisposableEffect(chromeVisible) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    // 想定外の破棄経路(編集画面への遷移など)向けの保険
    DisposableEffect(Unit) {
        onDispose { showSystemBars() }
    }

    // スライドショー: 一定間隔で自動送り(末尾で先頭へループ)。
    // 実行中は画面を常時点灯にし、画面タップ(=UI再表示)で停止する
    var slideshow by remember { mutableStateOf(false) }
    LaunchedEffect(slideshow) {
        while (slideshow) {
            kotlinx.coroutines.delay(4_000)
            if (!slideshow) break
            val next = (pagerState.currentPage + 1) % entries.size
            pagerState.animateScrollToPage(next)
        }
    }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) slideshow = false
    }
    DisposableEffect(slideshow) {
        val window = view.context.findActivity()?.window
        if (slideshow) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val backgroundAlpha = (1f - dismissProgress * 1.2f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha)),
    ) {
        // RAW⇔JPEG切替: エントリIDごとに「ペア相手側を表示中」を保持する
        var swappedIds by remember { mutableStateOf(emptySet<Long>()) }

        fun displayItemOf(entry: GalleryEntry) =
            entry.counterpart?.takeIf { entry.id in swappedIds } ?: entry.item

        // オーバーフローの「回転」による表示のみの回転(90°刻み、保存しない)。
        // 横向きのまま保存された写真をその場で確認する用途
        var viewRotations by remember { mutableStateOf(mapOf<Long, Float>()) }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries[page]
            val displayItem = displayItemOf(entry)
            // 現在ページのみ、グリッドのセルとの共有要素として扱う
            val sharedModifier =
                if (sharedScope != null && animatedScope != null && page == pagerState.currentPage) {
                    with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(sharedMediaKey(entry.id)),
                            animatedVisibilityScope = animatedScope,
                        )
                    }
                } else {
                    Modifier
                }
            when (displayItem.kind) {
                MediaKind.IMAGE -> PhotoPage(
                    item = displayItem,
                    imageModifier = sharedModifier.graphicsLayer {
                        rotationZ = viewRotations[entry.id] ?: 0f
                    },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onDismiss = { closeWithBarsRestored() },
                    onDismissProgress = { progress ->
                        if (page == pagerState.currentPage) dismissProgress = progress
                    },
                    onShowInfo = { showInfo = true },
                )

                MediaKind.VIDEO -> VideoPage(
                    entry = entry,
                    isActive = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    containerModifier = sharedModifier,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && dismissProgress == 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val current = entries[pagerState.currentPage.coerceIn(entries.indices)]
            ViewerTopBar(
                displayItem = displayItemOf(current),
                isPaired = current.isPaired,
                isFavorite = current.item.isFavorite ||
                    current.counterpart?.isFavorite == true,
                onClose = { closeWithBarsRestored() },
                onSwapRawJpeg = {
                    swappedIds = swappedIds.let {
                        if (current.id in it) it - current.id else it + current.id
                    }
                },
                onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(current) } },
                onShowInfo = { showInfo = true },
                onOpenPostPreview = onOpenPostPreview?.let { open -> { open(displayItemOf(current)) } },
                onStartSlideshow = if (entries.size > 1) {
                    {
                        chromeVisible = false
                        slideshow = true
                    }
                } else {
                    null
                },
                onSetWallpaper = if (displayItemOf(current).kind == MediaKind.IMAGE) {
                    { setAsWallpaper(context, displayItemOf(current).uri) }
                } else {
                    null
                },
                onRotateView = if (displayItemOf(current).kind == MediaKind.IMAGE) {
                    {
                        val id = current.id
                        viewRotations = viewRotations +
                            (id to ((viewRotations[id] ?: 0f) + 90f) % 360f)
                    }
                } else {
                    null
                },
            )
        }

        if (showInfo) {
            val current = entries[pagerState.currentPage.coerceIn(entries.indices)]
            MediaInfoSheet(
                info = infoCache[current.id],
                onDismiss = { showInfo = false },
                // 表示中の面(RAW⇔JPEG切替を反映)のヒストグラムを出す
                histogramUri = displayItemOf(current).uri,
            )
        }

        val currentEntry = entries[pagerState.currentPage.coerceIn(entries.indices)]
        AnimatedVisibility(
            visible = chromeVisible && dismissProgress == 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerBottomBar(
                entry = currentEntry,
                displayItem = displayItemOf(currentEntry),
                onShare = { shareTarget = it },
                onEditPhoto = onEditPhoto,
                onEditVideo = onEditVideo,
                onSendToLightroom = onSendToLightroom?.let { send -> { send(currentEntry) } },
                onDelete = onDelete?.let { delete -> { delete(currentEntry) } },
            )
        }

        // 共有シート導線(位置情報除去オプションを統合)
        shareTarget?.let { item ->
            ShareOptionsDialog(items = listOf(item), onDismiss = { shareTarget = null })
        }
    }
}

/**
 * ビューアの下部アクションバー(写真・動画共通の配置)。
 * 写真: 共有 / Lrで現像 / 編集(JPEGのみ) / 削除。
 * 動画: 共有 / 編集(LUT・カラー) / 削除 — 「Lrで現像」の位置に「編集」を置き
 * 写真と操作感を揃える。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerBottomBar(
    entry: GalleryEntry,
    displayItem: MediaItem,
    onShare: (MediaItem) -> Unit,
    onEditPhoto: ((MediaItem) -> Unit)?,
    onEditVideo: ((MediaItem) -> Unit)?,
    onSendToLightroom: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f),
                ),
            )
            .padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            ViewerAction(
                icon = Icons.Outlined.Share,
                label = "共有",
                onClick = { onShare(displayItem) },
            )
            if (displayItem.kind == MediaKind.VIDEO) {
                if (onEditVideo != null) {
                    ViewerAction(
                        icon = Icons.Outlined.Tune,
                        label = "編集",
                        onClick = { onEditVideo(displayItem) },
                    )
                }
            } else {
                if (onSendToLightroom != null) {
                    ViewerAction(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = "Lrで現像",
                        onClick = onSendToLightroom,
                    )
                }
                if (!displayItem.isRaw && onEditPhoto != null) {
                    ViewerAction(
                        icon = Icons.Outlined.Tune,
                        label = "編集",
                        onClick = { onEditPhoto(displayItem) },
                    )
                }
            }
            if (onDelete != null) {
                ViewerAction(
                    icon = Icons.Outlined.DeleteOutline,
                    label = "削除",
                    onClick = onDelete,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerTopBar(
    displayItem: MediaItem,
    isPaired: Boolean,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onSwapRawJpeg: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onShowInfo: () -> Unit = {},
    onOpenPostPreview: (() -> Unit)? = null,
    onStartSlideshow: (() -> Unit)? = null,
    onSetWallpaper: (() -> Unit)? = null,
    onRotateView: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                ),
            )
            // バーの表示状態に依存しないInsetsを使い、全画面⇔UI表示の
            // 切替でクロームの位置がずれないようにする
            .padding(WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "閉じる",
                    tint = Color.White,
                )
            }
            Text(
                text = formatViewerTitle(displayItem.dateTakenMs),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            if (displayItem.isRaw || isPaired) {
                Text(
                    text = if (displayItem.isRaw) "RAW" else "JPEG",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onStartSlideshow != null) {
                IconButton(onClick = onStartSlideshow) {
                    Icon(
                        Icons.Outlined.Slideshow,
                        contentDescription = "スライドショー",
                        tint = Color.White,
                    )
                }
            }
            if (onOpenPostPreview != null) {
                // 投稿プレビュー導線は編集画面と同じく上部トップバーに置く
                IconButton(onClick = onOpenPostPreview) {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = "投稿プレビュー",
                        tint = Color.White,
                    )
                }
            }
            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = if (isFavorite) "お気に入り解除" else "お気に入りに追加",
                        tint = if (isFavorite) Color(0xFFEF7B87) else Color.White,
                    )
                }
            }
            IconButton(onClick = onShowInfo) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "情報",
                    tint = Color.White,
                )
            }
            if (onSetWallpaper != null || onRotateView != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "その他のメニュー",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onRotateView != null) {
                            DropdownMenuItem(
                                text = { Text("回転(表示のみ)") },
                                onClick = {
                                    menuOpen = false
                                    onRotateView()
                                },
                            )
                        }
                        if (onSetWallpaper != null) {
                            DropdownMenuItem(
                                text = { Text("壁紙に設定") },
                                onClick = {
                                    menuOpen = false
                                    onSetWallpaper()
                                },
                            )
                        }
                    }
                }
            }
            if (isPaired) {
                // 同一構図のままRAW⇔JPEGを切り替える
                TextButton(onClick = onSwapRawJpeg) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (displayItem.isRaw) "JPEGを表示" else "RAWを表示",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * システムの壁紙設定(クロップUI付き)を開く。対応がない端末では
 * ACTION_ATTACH_DATA のチューザへフォールバックする。
 */
private fun setAsWallpaper(context: Context, uri: Uri) {
    val cropIntent = runCatching {
        android.app.WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)
    }.getOrNull()
    val intent = cropIntent ?: Intent(Intent.ACTION_ATTACH_DATA).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { Intent.createChooser(it, "壁紙に設定") }
    runCatching { context.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(context, "壁紙設定を開けませんでした", android.widget.Toast.LENGTH_SHORT).show()
    }
}
