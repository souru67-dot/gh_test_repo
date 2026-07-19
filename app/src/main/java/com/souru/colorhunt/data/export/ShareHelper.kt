package com.souru.colorhunt.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Shares images to other apps (Instagram, etc.) through the Android share sheet.
 *
 * Files are written to the app cache and exposed via [FileProvider] so no
 * storage permission is needed and the receiving app gets a temporary read
 * grant.
 */
object ShareHelper {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val SHARE_DIR = "shared"

    /** Write [bitmap] to a shareable cache file and return its content Uri. */
    suspend fun cacheForShare(context: Context, bitmap: Bitmap, name: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                val file = File(dir, "$name.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
            } catch (t: Throwable) {
                null
            }
        }

    /** ACTION_SEND for a single image. */
    fun shareSingle(context: Context, uri: Uri, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, intent, chooserTitle)
    }

    /** ACTION_SEND_MULTIPLE for several images (used from Phase 3 onward). */
    fun shareMultiple(context: Context, uris: List<Uri>, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, intent, chooserTitle)
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
