package com.souru.lumina.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** メディア権限の3状態。どの状態でもアプリはクラッシュせず動作する。 */
sealed interface MediaAccess {
    /** 写真・動画へのフルアクセス。 */
    data object Full : MediaAccess

    /** Android 14+ の「一部の写真と動画のみ許可」。 */
    data object Partial : MediaAccess

    /** アクセスなし。 */
    data object Denied : MediaAccess
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun mediaAccessState(context: Context): MediaAccess = when {
    granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
        granted(context, Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.Full

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial

    else -> MediaAccess.Denied
}

/** フル許可・一部許可のどちらかでメディアが読めるか。 */
fun hasMediaAccess(context: Context): Boolean = mediaAccessState(context) != MediaAccess.Denied

/** Android 14+ の「一部の写真と動画のみ許可」状態か。 */
fun hasPartialMediaAccess(context: Context): Boolean =
    mediaAccessState(context) == MediaAccess.Partial

tailrec fun Context.findActivityContext(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityContext()
    else -> null
}

/**
 * システムの権限ダイアログをまだ出せる見込みがあるか。
 * false(完全拒否)ならアプリ設定画面へ誘導する。
 */
fun canRequestMediaPermissionAgain(context: Context): Boolean {
    val activity = context.findActivityContext() ?: return true
    return mediaPermissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

val mediaPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    }
