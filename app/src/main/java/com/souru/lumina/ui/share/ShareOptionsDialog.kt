package com.souru.lumina.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.ui.pro.ProFeature
import com.souru.lumina.ui.pro.ProUpsellDialog
import com.souru.lumina.ui.pro.rememberIsPro
import com.souru.lumina.util.Sharing

/**
 * 共有の導線。位置情報を除去できるJPEGが含まれる場合のみ選択肢を出し、
 * それ以外は即座にシステム共有シートを開く(余計なワンステップを挟まない)。
 * どの選択肢も最終的に Intent.createChooser のシステム共有シートへ繋がる。
 */
@Composable
fun ShareOptionsDialog(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isPro = rememberIsPro()
    var showUpsell by remember { mutableStateOf(false) }

    if (showUpsell) {
        ProUpsellDialog(ProFeature.GEO_SHARE, onDismiss = { showUpsell = false; onDismiss() })
        return
    }

    // 位置除去できる対象が無ければダイアログを出さず直接共有する
    if (!Sharing.anyStrippable(items)) {
        Sharing.shareMultiple(context, items, stripLocation = false)
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("共有") },
        text = {
            Column {
                ShareRow(
                    icon = Icons.Outlined.Share,
                    title = "そのまま共有",
                    subtitle = "撮影情報(位置情報を含む)をそのまま共有します",
                    onClick = {
                        onDismiss()
                        Sharing.shareMultiple(context, items, stripLocation = false)
                    },
                )
                ShareRow(
                    icon = Icons.Outlined.LocationOff,
                    title = if (isPro) "位置情報を除去して共有" else "位置情報を除去して共有 (Pro)",
                    subtitle = "JPEGのGPS情報を取り除いたコピーを共有します",
                    onClick = {
                        if (isPro) {
                            onDismiss()
                            Sharing.shareMultiple(context, items, stripLocation = true)
                        } else {
                            showUpsell = true
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun ShareRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
