package com.souru.lumina.ui.library

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AppSettingsAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ライブラリタブ。お気に入り・ゴミ箱・LUT管理などの入口をまとめる。
 */
@Composable
fun LibraryScreen(
    onOpenFavorites: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLutManager: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(bottom = bottomContentPadding),
    ) {
        Text(
            text = "ライブラリ",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        )
        LibraryRow(
            icon = Icons.Outlined.FavoriteBorder,
            title = "お気に入り",
            subtitle = "お気に入りに追加した写真と動画",
            onClick = onOpenFavorites,
        )
        LibraryRow(
            icon = Icons.Outlined.DeleteOutline,
            title = "ゴミ箱",
            subtitle = "削除したアイテム(約30日で自動削除)",
            onClick = onOpenTrash,
        )
        LibraryRow(
            icon = Icons.Outlined.Palette,
            title = "LUTライブラリ",
            subtitle = "動画用 .cube LUT の管理",
            onClick = onOpenLutManager,
        )
        val context = LocalContext.current
        LibraryRow(
            icon = Icons.Outlined.AppSettingsAlt,
            title = "デフォルトのアプリ設定",
            subtitle = "画像・動画を開く既定アプリにLuminaを設定",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            },
        )
    }
}

@Composable
internal fun LibraryRow(
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
