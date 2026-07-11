package com.souru.lumina.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.lumina.data.luts.LutCategory
import com.souru.lumina.data.luts.LutInfo

/** LUTライブラリの管理画面(.cubeのインポート・削除)。 */
@Composable
fun LutManagerScreen(
    onClose: () -> Unit,
    viewModel: LutManagerViewModel = viewModel(factory = LutManagerViewModel.Factory),
) {
    val luts by viewModel.luts.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<LutInfo?>(null) }
    val isPro = com.souru.lumina.ui.pro.rememberIsPro()
    var showUpsell by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    if (showUpsell) {
        com.souru.lumina.ui.pro.ProUpsellDialog(
            com.souru.lumina.ui.pro.ProFeature.CUBE_IMPORT,
            onDismiss = { showUpsell = false },
        )
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

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
                text = "LUTライブラリ",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                if (isPro) importLauncher.launch(arrayOf("*/*")) else showUpsell = true
            }) {
                Icon(Icons.Default.Add, contentDescription = ".cubeをインポート", tint = MaterialTheme.colorScheme.primary)
            }
        }

        val imported = luts.filterNot { it.isPreset }
        LazyColumn(Modifier.fillMaxSize()) {
            // 標準プリセットはカテゴリごとのセクションで表示する
            LutCategory.entries.forEach { category ->
                val inCategory = luts.filter { it.preset?.category == category }
                if (inCategory.isNotEmpty()) {
                    item(key = "header-${category.name}") {
                        SectionHeader(
                            title = category.label,
                            note = if (category == LutCategory.entries.first()) {
                                "標準LUTはLog素材向け(S-Cinetone for Mobile等)。通常のRec.709素材には濃すぎる場合があります"
                            } else {
                                null
                            },
                        )
                    }
                    items(inCategory, key = { it.id }) { lut ->
                        LutListRow(
                            lut = lut,
                            thumbnail = thumbnails[lut.id],
                            onDelete = null,
                        )
                    }
                }
            }
            item(key = "header-imported") {
                SectionHeader(title = "インポート", note = null)
            }
            if (imported.isEmpty()) {
                item(key = "imported-empty") {
                    Text(
                        text = "インポートしたLUTはありません。右上の+から .cube を追加できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(imported, key = { it.id }) { lut ->
                    LutListRow(
                        lut = lut,
                        thumbnail = thumbnails[lut.id],
                        onDelete = { deleteTarget = lut },
                    )
                }
            }
        }
    }

    // (プリセットにはonDelete=nullを渡すため削除ダイアログは出ない)
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("LUTを削除") },
            text = { Text("「${target.name}」をライブラリから削除しますか?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, note: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LutListRow(
    lut: LutInfo,
    thumbnail: android.graphics.Bitmap?,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // サンプル画像への適用例
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = lut.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Text(
                text = if (lut.isPreset) "標準プリセット" else "%.1f KB".format(lut.file.length() / 1024f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
