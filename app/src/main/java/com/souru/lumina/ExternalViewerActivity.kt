package com.souru.lumina

import android.content.ContentUris
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.pairing.PairCandidate
import com.souru.lumina.data.pairing.RawJpegPairer
import com.souru.lumina.ui.theme.LuminaTheme
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.hasMediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 外部アプリからの ACTION_VIEW (画像・動画MIME) を受けるエントリポイント。
 * 渡されたURIが自ライブラリ(MediaStore)内のアイテムなら通常のビューアに
 * 接続して前後スワイプも有効化し、そうでなければ単体表示にフォールバックする。
 */
class ExternalViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val mimeHint = intent?.type
        setContent {
            LuminaTheme {
                ExternalViewerRoute(
                    uri = uri,
                    mimeHint = mimeHint,
                    onClose = { finish() },
                )
            }
        }
    }
}

private sealed interface ExternalViewerState {
    data object Loading : ExternalViewerState
    data class Error(val message: String) : ExternalViewerState
    data class Ready(val entries: List<GalleryEntry>, val index: Int) : ExternalViewerState
}

@Composable
private fun ExternalViewerRoute(uri: Uri?, mimeHint: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ExternalViewerState>(ExternalViewerState.Loading) }

    LaunchedEffect(uri) {
        state = if (uri == null) {
            ExternalViewerState.Error("表示するメディアが指定されていません")
        } else {
            withContext(Dispatchers.IO) {
                try {
                    resolveEntries(context, uri, mimeHint)
                } catch (se: SecurityException) {
                    ExternalViewerState.Error("このメディアを読み取る権限がありません")
                } catch (t: Throwable) {
                    ExternalViewerState.Error("メディアを開けませんでした")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val s = state) {
            ExternalViewerState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ExternalViewerState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            ) {
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) {
                    Text("閉じる", color = MaterialTheme.colorScheme.primary)
                }
            }

            is ExternalViewerState.Ready -> ViewerScreen(
                entries = s.entries,
                initialIndex = s.index,
                onClose = onClose,
            )
        }
    }
}

private fun resolveEntries(
    context: Context,
    uri: Uri,
    mimeHint: String?,
): ExternalViewerState {
    // MediaStore由来のURIで、かつメディア権限があれば自ライブラリに接続する
    val mediaId = if (uri.authority == MediaStore.AUTHORITY) {
        runCatching { ContentUris.parseId(uri) }.getOrNull()
    } else {
        null
    }
    if (mediaId != null && hasMediaAccess(context)) {
        val repo = (context.applicationContext as LuminaApplication).container.mediaRepository
        val all = runCatching { repo.queryAll() }.getOrElse { emptyList() }
        if (all.any { it.id == mediaId }) {
            val pairs = runCatching {
                RawJpegPairer.pair(
                    all.filter { it.kind == MediaKind.IMAGE }
                        .map { PairCandidate(it.id, it.baseName, it.dateTakenMs, it.isRaw) },
                )
            }.getOrElse { emptyMap() }
            val byId = all.associateBy { it.id }
            // ギャラリーの「すべて」と同じくペアのRAW側は統合する
            val entries = all
                .filterNot { it.isRaw && pairs.containsKey(it.id) }
                .map { GalleryEntry(it, pairs[it.id]?.let(byId::get)) }
            val index = entries.indexOfFirst { it.id == mediaId || it.counterpart?.id == mediaId }
            if (index >= 0) return ExternalViewerState.Ready(entries, index)
        }
    }
    // ライブラリ外(他アプリのFileProvider等)は単体表示にフォールバック
    val item = probeSingle(context, uri, mimeHint)
        ?: return ExternalViewerState.Error("メディアを開けませんでした")
    return ExternalViewerState.Ready(listOf(GalleryEntry(item, null)), 0)
}

/** 任意のcontent:// URIからビューア表示に必要な最小情報を読む。 */
private fun probeSingle(context: Context, uri: Uri, mimeHint: String?): MediaItem? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: mimeHint
    var name: String? = null
    var size = 0L
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameCol >= 0 && !cursor.isNull(nameCol)) name = cursor.getString(nameCol)
                if (sizeCol >= 0 && !cursor.isNull(sizeCol)) size = cursor.getLong(sizeCol)
            }
        }
    }
    // 実際に読めるかを確認する(権限がなければSecurityExceptionが呼び出し元へ届く)
    resolver.openInputStream(uri)?.use { } ?: return null

    val resolvedMime = mime ?: "image/*"
    val kind = if (resolvedMime.startsWith("video")) MediaKind.VIDEO else MediaKind.IMAGE
    return MediaItem(
        id = 0L,
        uri = uri,
        displayName = name ?: "メディア",
        mimeType = resolvedMime,
        dateTakenMs = System.currentTimeMillis(),
        sizeBytes = size,
        width = 0,
        height = 0,
        durationMs = 0,
        kind = kind,
    )
}
