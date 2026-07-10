package com.souru.lumina.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.MediaInfo

/**
 * ビューアの情報シート(EXIF/動画メタデータ)。取得できなかった項目は
 * 行ごと表示しない。撮影パラメータは1行のカメラアプリ風表示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    info: MediaInfo?,
    onDismiss: () -> Unit,
    histogramUri: Uri? = null,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            if (info == null) {
                Text(
                    text = "情報を取得できませんでした",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = info.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Text(
                text = listOfNotNull(info.format, info.fileSize, info.takenAt)
                    .joinToString(" ・ "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 撮影パラメータ(カメラアプリ風の1行)
            info.paramsLine?.let { params ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = params + (info.exposureBias?.let { " ・ $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            info.videoLine?.let { line ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ヒストグラム(輝度+RGB)。撮影者向けの露出・色被り確認
            histogramUri?.let { uri ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "ヒストグラム",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                HistogramSection(uri)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))

            InfoRow("カメラ", info.camera)
            InfoRow("レンズ", info.lens)
            InfoRow("解像度", info.resolution)

            if (info.pairFiles.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "RAW+JPEGペア",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                info.pairFiles.forEach { (name, size) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            info.gps?.let { (lat, lon) ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "撮影場所: %.5f, %.5f(タップで地図を開く)".format(lat, lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("geo:$lat,$lon?q=$lat,$lon"),
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}
