package com.souru.lumina.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souru.lumina.LuminaApplication

/** ゲート対象のPro機能。ペイウォールの見出し・説明に使う。 */
enum class ProFeature(val title: String, val description: String) {
    ALL_LUTS("すべてのLUT", "エモ・ノスタルジー系や Faded Film を含む全プリセットをアンロックします。"),
    CUBE_IMPORT(".cube インポート", "お好みの 3D LUT(.cube)を取り込んで使えるようになります。"),
    POST_PREVIEW("投稿プレビュー", "Instagram のグリッド/フィード/リールでの見え方を投稿前に確認できます。"),
    GEO_SHARE("位置情報を除去して共有", "GPS 等の位置情報を取り除いたコピーを共有します。"),
}

/** そのLUTがPro専用か(無料は Clean Contrast と Mono Cinema のみ、取り込みLUTはPro)。 */
fun isProLut(info: com.souru.lumina.data.luts.LutInfo): Boolean = info.preset?.free != true

/** Context から Activity を辿る(購入フロー起動にActivityが必要)。 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 現在Proが解放されているかを購読する。 */
@Composable
fun rememberIsPro(): Boolean {
    val context = LocalContext.current
    val entitlement = remember {
        (context.applicationContext as LuminaApplication).container.entitlementRepository
    }
    val isPro by entitlement.isPro.collectAsStateWithLifecycle()
    return isPro
}

/**
 * Pro機能に触れたときの自然な案内。無料機能を邪魔しない範囲で、
 * 機能紹介と購入/復元だけを提示する軽量ダイアログ。
 */
@Composable
fun ProUpsellDialog(feature: ProFeature, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as LuminaApplication).container }
    val entitlement = container.entitlementRepository
    val price by entitlement.formattedPrice.collectAsStateWithLifecycle()
    val connected by entitlement.billingConnected.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${feature.title} は Pro 機能です") },
        text = {
            Column {
                Text(feature.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Lumina Pro(買い切り)で以下もすべてアンロック: 全LUT・.cube取り込み・" +
                        "投稿プレビュー・SNSセーフ書き出し・動画フレーム書き出し・位置情報除去共有。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { context.findActivity()?.let { entitlement.launchPurchase(it) } },
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !connected -> "ストアに接続中…"
                        price != null -> "Proを購入 ($price)"
                        else -> "Proを購入"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { entitlement.refresh(); onDismiss() }) { Text("購入を復元") }
        },
    )
}
