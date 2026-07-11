package com.souru.lumina.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)
private val viewerTitleFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.JAPAN)

fun formatDateHeader(date: LocalDate): String = date.format(headerFormatter)

fun formatViewerTitle(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(viewerTitleFormatter)

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
