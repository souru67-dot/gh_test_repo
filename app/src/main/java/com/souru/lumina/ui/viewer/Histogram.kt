package com.souru.lumina.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 輝度+RGBのヒストグラム(256ビン)。撮影者向けの露出・色被り確認用。
 * 値は各チャンネル独立に最大ビンで正規化した高さ(0..1)。
 */
class HistogramData(
    val luma: FloatArray,
    val r: FloatArray,
    val g: FloatArray,
    val b: FloatArray,
)

/**
 * メディアのヒストグラムを計算する。写真・動画・RAW(埋め込みプレビュー)を
 * 問わず contentResolver.loadThumbnail の縮小ビットマップから集計するため
 * 軽量(約256px)。形状確認には十分な精度。失敗時は null。
 */
suspend fun computeHistogram(context: Context, uri: Uri): HistogramData? =
    withContext(Dispatchers.Default) {
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
        }.getOrNull() ?: return@withContext null

        val bins = 256
        val rH = IntArray(bins)
        val gH = IntArray(bins)
        val bH = IntArray(bins)
        val lH = IntArray(bins)
        val px = IntArray(bitmap.width * bitmap.height)
        val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        safe.getPixels(px, 0, safe.width, 0, 0, safe.width, safe.height)
        for (c in px) {
            val r = c ushr 16 and 0xFF
            val g = c ushr 8 and 0xFF
            val b = c and 0xFF
            rH[r]++
            gH[g]++
            bH[b]++
            // BT.709 輝度
            val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            lH[l]++
        }
        if (safe !== bitmap) safe.recycle()
        bitmap.recycle()

        fun normalize(h: IntArray): FloatArray {
            val max = h.max().coerceAtLeast(1)
            return FloatArray(bins) { i -> h[i].toFloat() / max }
        }
        HistogramData(normalize(lH), normalize(rH), normalize(gH), normalize(bH))
    }

/** URIからヒストグラムを非同期計算して描画する(情報シート用)。 */
@Composable
fun HistogramSection(uri: Uri, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val data by produceState<HistogramData?>(initialValue = null, uri) {
        value = computeHistogram(context, uri)
    }
    data?.let { HistogramChart(it, modifier) }
}

/**
 * ヒストグラムの描画。輝度は白の面、RGBは加算風(Plus)の半透明面で重ねる。
 * 黒基調UIに合わせ、目盛りは1/4刻みの薄い縦線のみ。
 */
@Composable
fun HistogramChart(data: HistogramData, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val w = size.width
        val h = size.height
        val bins = data.luma.size

        fun pathOf(values: FloatArray): Path {
            val p = Path()
            p.moveTo(0f, h)
            for (i in 0 until bins) {
                val x = w * i / (bins - 1)
                val y = h * (1f - values[i])
                p.lineTo(x, y)
            }
            p.lineTo(w, h)
            p.close()
            return p
        }

        // 目盛り(1/4刻み)
        for (i in 1..3) {
            val x = w * i / 4f
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f,
            )
        }

        // RGB(加算合成でカメラのヒストグラム風に)
        drawPath(pathOf(data.r), Color(0xFFE05555).copy(alpha = 0.55f), style = Fill, blendMode = BlendMode.Plus)
        drawPath(pathOf(data.g), Color(0xFF55C860).copy(alpha = 0.55f), style = Fill, blendMode = BlendMode.Plus)
        drawPath(pathOf(data.b), Color(0xFF5B7FE0).copy(alpha = 0.55f), style = Fill, blendMode = BlendMode.Plus)

        // 輝度(白の輪郭線で全体形状を示す)
        drawPath(pathOf(data.luma), Color.White.copy(alpha = 0.85f), style = Stroke(width = 2f))

        // 外枠
        drawRect(color = Color.White.copy(alpha = 0.15f), style = Stroke(width = 1f))
    }
}
