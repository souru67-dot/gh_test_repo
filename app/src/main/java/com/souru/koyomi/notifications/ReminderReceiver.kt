package com.souru.koyomi.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.souru.koyomi.MainActivity
import com.souru.koyomi.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CalendarProvider fires ACTION_EVENT_REMINDER at each alarm time; we look up
 * the firing alerts and post our own notifications so reminders work even
 * without another calendar app installed.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val alarmTime = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val projection = arrayOf(
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.TITLE,
            CalendarContract.CalendarAlerts.EVENT_LOCATION,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.ALL_DAY,
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                projection,
                "${CalendarContract.CalendarAlerts.ALARM_TIME} = ?",
                arrayOf(alarmTime.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    postNotification(
                        context = context,
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty(),
                        location = cursor.getString(2),
                        begin = cursor.getLong(3),
                        allDay = cursor.getInt(4) != 0,
                    )
                }
            }
        }
    }

    private fun postNotification(
        context: Context,
        eventId: Long,
        title: String,
        location: String?,
        begin: Long,
        allDay: Boolean,
    ) {
        val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val timeText = if (allDay) {
            context.getString(R.string.all_day)
        } else {
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(start)
        }
        val text = listOfNotNull(timeText, location?.takeIf { it.isNotBlank() })
            .joinToString("  ")

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EPOCH_DAY, start.toLocalDate().toEpochDay())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { context.getString(R.string.untitled) })
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((eventId xor begin).toInt(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}
