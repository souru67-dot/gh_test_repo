package com.souru.lumina.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * アプリ共通のローディング表示。各画面でバラバラに置いていた
 * CircularProgressIndicator を一本化し、控えめで統一された見た目にする
 * (細いストローク・小さめ・onSurfaceVariant色)。写真が主役の黒基調UIで
 * 主張しすぎないことを狙う。
 *
 * グリッド系はサムネイルが Coil のクロスフェードで順次現れるため、
 * このインジケータは「まだ何も出せない初回だけ」中央に控えめに出す。
 */
@Composable
fun LuminaLoading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}
