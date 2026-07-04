package com.souru.lumina.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** フル許可・一部許可のどちらかでメディアが読めるか。 */
fun hasMediaAccess(context: Context): Boolean =
    granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
        granted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
        hasPartialMediaAccess(context)

/** Android 14+ の「一部の写真と動画のみ許可」状態か。 */
fun hasPartialMediaAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
        !granted(context, Manifest.permission.READ_MEDIA_IMAGES)

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
