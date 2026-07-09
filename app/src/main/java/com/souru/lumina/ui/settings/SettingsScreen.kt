package com.souru.lumina.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.souru.lumina.BuildConfig
import com.souru.lumina.LuminaApplication
import kotlinx.coroutines.launch

/** Context から Activity を辿る(課金フロー起動にActivityが必要)。 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as LuminaApplication).container }
    val entitlement = container.entitlementRepository
    val scope = container.appScope

    val isPro by entitlement.isPro.collectAsStateWithLifecycle()
    val price by entitlement.formattedPrice.collectAsStateWithLifecycle()
    val connected by entitlement.billingConnected.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る", tint = Color.White)
            }
            Text("設定", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }

        // Proステータスカード
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isPro) "Lumina Pro 解放中" else "Lumina Pro",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (isPro) {
                    "すべての機能をご利用いただけます。ありがとうございます。"
                } else {
                    "全LUT・.cubeインポート・投稿プレビュー・SNSセーフ書き出し・" +
                        "動画フレーム書き出し・位置情報除去共有をアンロック(買い切り)"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (!isPro) {
                Spacer(Modifier.size(16.dp))
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
                OutlinedButton(
                    onClick = { entitlement.refresh() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("購入を復元")
                }
            }
        }

        // デバッグ限定: Pro状態トグル
        if (BuildConfig.DEBUG) {
            val debugOverride by container.settingsRepository.proDebugOverride
                .collectAsStateWithLifecycle(initialValue = false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { entitlement.setDebugOverride(!debugOverride) }
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("[DEBUG] Proを有効化", color = Color.White)
                    Text(
                        "開発用の擬似アンロック(リリースでは無効)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = debugOverride,
                    onCheckedChange = { scope.launch { entitlement.setDebugOverride(it) } },
                )
            }
        }
    }
}
