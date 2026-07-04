package com.souru.lumina.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem

/**
 * Lightroom Mobile 連携。RAW現像はLightroomに任せるため、
 * ペア写真では常にRAW側のURIを渡す。
 */
object Lightroom {

    const val PACKAGE_NAME = "com.adobe.lrmobile"

    /** ペアなら常にRAW側を返す。 */
    fun rawSideOf(entry: GalleryEntry): MediaItem = when {
        entry.item.isRaw -> entry.item
        entry.counterpart?.isRaw == true -> entry.counterpart
        else -> entry.item
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * 1枚をLightroomへ。ACTION_EDIT を優先し、受け付けられない場合は
     * ACTION_SEND にフォールバックする。成功可否を返す。
     */
    fun open(context: Context, item: MediaItem): Boolean {
        if (!isInstalled(context)) return false
        val edit = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mimeType)
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(edit)
            return true
        } catch (e: ActivityNotFoundException) {
            // ACTION_SEND にフォールバック
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifEmpty { "image/x-adobe-dng" }
            putExtra(Intent.EXTRA_STREAM, item.uri)
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(send)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** 複数枚をまとめてLightroomへ(ACTION_SEND_MULTIPLE)。 */
    fun openMultiple(context: Context, items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        if (items.size == 1) return open(context, items.first())
        if (!isInstalled(context)) return false
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (items.all { it.isRaw }) "image/x-adobe-dng" else "image/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList<Uri>(items.map { it.uri }),
            )
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun openPlayStore(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME")),
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME"),
                ),
            )
        }
    }
}

/** OSの共有シートで共有する。 */
fun shareMediaItem(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
