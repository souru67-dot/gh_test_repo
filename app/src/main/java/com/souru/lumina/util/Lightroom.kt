package com.souru.lumina.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem

/**
 * Lightroom Mobile 連携。RAW現像はLightroomに任せるため、
 * ペア写真では常にRAW側のURIを渡す。
 *
 * 注意: Android 11+ ではマニフェストの <queries> 宣言がないと
 * getPackageInfo / resolveActivity が常に失敗する。
 */
object Lightroom {

    const val PACKAGE_NAME = "com.adobe.lrmobile"
    private const val TAG = "Lightroom"

    /** ペアなら常にRAW側を返す。 */
    fun rawSideOf(entry: GalleryEntry): MediaItem = when {
        entry.item.isRaw -> entry.item
        entry.counterpart?.isRaw == true -> entry.counterpart
        else -> entry.item
    }

    fun isInstalled(context: Context): Boolean {
        val byPackage = try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        // デバッグ用: 可視性の問題切り分けのため判定結果を出力する
        Log.d(TAG, "isInstalled(getPackageInfo)=$byPackage")
        return byPackage
    }

    /** Lightroom が EDIT/SEND を受け付けるかを resolveActivity で確認する。 */
    private fun resolves(context: Context, intent: Intent): Boolean {
        val resolved = context.packageManager.resolveActivity(intent, 0)
        Log.d(TAG, "resolveActivity(${intent.action})=${resolved?.activityInfo?.name}")
        return resolved != null
    }

    /**
     * 1枚をLightroomへ。ACTION_EDIT を優先し、受け付けられない場合は
     * ACTION_SEND にフォールバックする。成功可否を返す。
     */
    fun open(context: Context, item: MediaItem): Boolean {
        if (!isInstalled(context)) {
            Log.d(TAG, "open: Lightroom not visible/installed")
            return false
        }
        val edit = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mimeType)
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (resolves(context, edit)) {
            try {
                context.startActivity(edit)
                Log.d(TAG, "open: launched via ACTION_EDIT")
                return true
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "open: ACTION_EDIT failed, falling back to ACTION_SEND", e)
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifEmpty { "image/x-adobe-dng" }
            putExtra(Intent.EXTRA_STREAM, item.uri)
            setPackage(PACKAGE_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(send)
            Log.d(TAG, "open: launched via ACTION_SEND")
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "open: ACTION_SEND failed", e)
            false
        }
    }

    /** 複数枚をまとめてLightroomへ(ACTION_SEND_MULTIPLE)。 */
    fun openMultiple(context: Context, items: List<MediaItem>): Boolean {
        if (items.isEmpty()) return false
        if (items.size == 1) return open(context, items.first())
        if (!isInstalled(context)) {
            Log.d(TAG, "openMultiple: Lightroom not visible/installed")
            return false
        }
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

/** 複数アイテムをOSの共有シートで共有する。 */
fun shareMediaItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        shareMediaItem(context, items.first())
        return
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
