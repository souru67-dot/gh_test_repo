package com.souru.colorhunt.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.souru.colorhunt.MainActivity
import com.souru.colorhunt.R

/**
 * Posts the daily "today's color" notification (Phase 4). Creates its channel
 * lazily and no-ops safely if the POST_NOTIFICATIONS permission isn't granted.
 */
object ThemeNotifier {

    const val CHANNEL_ID = "daily_theme_color"
    private const val NOTIFICATION_ID = 4001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.roulette_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.roulette_channel_desc) }
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyTodayColor(context: Context, colorName: String) {
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.roulette_notif_title))
            .setContentText(context.getString(R.string.roulette_notif_text, colorName))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        // Safe on API 33+: if permission is missing, this simply does not post.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (t: SecurityException) {
            // Permission revoked between check and post — ignore.
        }
    }
}
