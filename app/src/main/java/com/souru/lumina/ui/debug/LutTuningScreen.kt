package com.souru.lumina.ui.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.luts.FadedFilmTuning
import com.souru.lumina.data.luts.LutPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * デバッグ限定の LUT チューニング画面。
 *
 * 目的: 計測値で追い込んだ Faded Film の「最後の感じ」を人の目で仕上げるための道具。
 *  - 自分の写真1枚と参考画像を並べて表示
 *  - 生成パラメータをスライダーで即時反映(CPUで縮小プレビューに適用)
 *  - 「.cube書き出し」で現在値からLUTを生成しライブラリへ取り込み
 *  - 「Logへ出力」で FadedFilmParams に貼れる定数を出力
 *
 * ここで確定した値を LutPresets.FadedFilmParams の定数へ反映する運用。
 */
@Composable
fun LutTuningScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val repo = remember { (context.applicationContext as LuminaApplication).container.lutRepository }

    var tuning by remember { mutableStateOf(FadedFilmTuning()) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var reference by remember { mutableStateOf<ImageBitmap?>(null) }

    val pickSource = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch { source = decodeDownscaled(context, uri, 640) }
    }
    val pickReference = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            reference = decodeDownscaled(context, uri, 640)?.asImageBitmap()
        }
    }

    // 写真かパラメータが変わるたびにCPUでLUTを適用してプレビュー更新
    LaunchedEffect(source, tuning) {
        val src = source ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { applyTuning(src, tuning).asImageBitmap() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る", tint = Color.White)
                }
                Text("LUTチューニング (Faded Film)", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }

            // 参考画像 | プレビュー の並置
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledImage("参考画像", reference, Modifier.weight(1f)) { pickReference.launch("image/*") }
                LabeledImage("プレビュー", preview, Modifier.weight(1f)) { pickSource.launch("image/*") }
            }
            Text(
                "上をタップで画像を選択(左=参考, 右=自分の写真にLUT適用)",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )

            // 操作ボタン
            Row(Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                repo.importCubeText(
                                    "Faded Film (tuned)",
                                    LutPresets.generateFadedCubeText(tuning, "Lumina Faded Film (tuned)"),
                                )
                            }.onSuccess {
                                snackbar.showSnackbar("ライブラリに書き出しました: ${it.name}")
                            }.onFailure {
                                snackbar.showSnackbar("書き出し失敗: ${it.message}")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(".cube書き出し") }
                OutlinedButton(
                    onClick = { Log.i("LutTuning", tuning.toKotlinConstants()) },
                    modifier = Modifier.weight(1f),
                ) { Text("Logへ出力") }
                OutlinedButton(onClick = { tuning = FadedFilmTuning() }) { Text("既定") }
            }

            // スライダー群
            Section("トーン")
            TuneSlider("黒点 blackPoint", tuning.blackPoint, 0f, 0.15f) { tuning = tuning.copy(blackPoint = it) }
            TuneSlider("白点 whitePoint", tuning.whitePoint, 0.80f, 1.0f) { tuning = tuning.copy(whitePoint = it) }
            TuneSlider("コントラスト", tuning.contrast, 1.0f, 1.7f) { tuning = tuning.copy(contrast = it) }
            TuneSlider("ピボット", tuning.contrastPivot, 0.35f, 0.60f) { tuning = tuning.copy(contrastPivot = it) }

            Section("スプリットトーン(シャドウ=緑 / ハイライト=暖色)")
            TuneSlider("シャドウ 赤", tuning.shadowTintR, -0.08f, 0.04f) { tuning = tuning.copy(shadowTintR = it) }
            TuneSlider("シャドウ 緑", tuning.shadowTintG, -0.03f, 0.06f) { tuning = tuning.copy(shadowTintG = it) }
            TuneSlider("シャドウ 青", tuning.shadowTintB, -0.03f, 0.06f) { tuning = tuning.copy(shadowTintB = it) }
            TuneSlider("ハイライト 赤", tuning.highlightTintR, -0.02f, 0.09f) { tuning = tuning.copy(highlightTintR = it) }
            TuneSlider("ハイライト 緑", tuning.highlightTintG, -0.02f, 0.06f) { tuning = tuning.copy(highlightTintG = it) }
            TuneSlider("ハイライト 青", tuning.highlightTintB, -0.09f, 0.02f) { tuning = tuning.copy(highlightTintB = it) }

            Section("彩度・色相")
            TuneSlider("全体彩度", tuning.baseSaturation, 0.5f, 1.1f) { tuning = tuning.copy(baseSaturation = it) }
            TuneSlider("緑 彩度", tuning.greenDesat, 0.3f, 1.2f) { tuning = tuning.copy(greenDesat = it) }
            TuneSlider("緑 黄シフト", tuning.greenHueShift, 0f, 0.12f) { tuning = tuning.copy(greenHueShift = it) }
            TuneSlider("暖色 維持", tuning.warmSatKeep, 0.8f, 1.5f) { tuning = tuning.copy(warmSatKeep = it) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun LabeledImage(
    label: String,
    bmp: ImageBitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    "未選択",
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("画像を選択") }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TuneSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            "$label: ${"%.4f".format(value)}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

/** 縮小デコード。長辺を [maxDim] に収めてメモリと処理を軽くする。 */
private suspend fun decodeDownscaled(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }

/** CPUで Faded Film 変換を縮小ビットマップに適用する(デバッグプレビュー用)。 */
private fun applyTuning(src: Bitmap, t: FadedFilmTuning): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val out = FloatArray(3)
    for (i in px.indices) {
        val c = px[i]
        val a = c ushr 24 and 0xFF
        val r = (c ushr 16 and 0xFF) / 255f
        val g = (c ushr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val res = LutPresets.fadedFilm(t, r, g, b)
        out[0] = res[0]; out[1] = res[1]; out[2] = res[2]
        val ri = (out[0] * 255f).roundToInt().coerceIn(0, 255)
        val gi = (out[1] * 255f).roundToInt().coerceIn(0, 255)
        val bi = (out[2] * 255f).roundToInt().coerceIn(0, 255)
        px[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** 現在のチューニング値を FadedFilmParams に貼れる形で文字列化。 */
private fun FadedFilmTuning.toKotlinConstants(): String = buildString {
    append("FadedFilmParams 収束値:\n")
    append("  BLACK_POINT = ${"%.4f".format(blackPoint)}f\n")
    append("  WHITE_POINT = ${"%.4f".format(whitePoint)}f\n")
    append("  CONTRAST = ${"%.4f".format(contrast)}f\n")
    append("  CONTRAST_PIVOT = ${"%.4f".format(contrastPivot)}f\n")
    append("  SHADOW_TINT_R/G/B = ${"%.4f".format(shadowTintR)}f / ${"%.4f".format(shadowTintG)}f / ${"%.4f".format(shadowTintB)}f\n")
    append("  HIGHLIGHT_TINT_R/G/B = ${"%.4f".format(highlightTintR)}f / ${"%.4f".format(highlightTintG)}f / ${"%.4f".format(highlightTintB)}f\n")
    append("  BASE_SATURATION = ${"%.4f".format(baseSaturation)}f\n")
    append("  GREEN_DESAT = ${"%.4f".format(greenDesat)}f\n")
    append("  GREEN_HUE_SHIFT = ${"%.4f".format(greenHueShift)}f\n")
    append("  WARM_SAT_KEEP = ${"%.4f".format(warmSatKeep)}f\n")
}
