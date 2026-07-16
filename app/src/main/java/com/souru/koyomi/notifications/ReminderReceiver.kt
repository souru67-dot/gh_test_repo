package com.souru.koyomi.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
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
            CalendarContract.CalendarAlerts._ID,
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.TITLE,
            CalendarContract.CalendarAlerts.EVENT_LOCATION,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.ALL_DAY,
        )
        // Only pick up alerts that have not been shown yet. CalendarProvider can
        // re-broadcast the same alarm (on reboot, sync, or repeated firings);
        // filtering out already-fired/dismissed alerts and marking each one
        // dismissed after posting keeps a reminder to a single notification.
        val selection = "${CalendarContract.CalendarAlerts.ALARM_TIME} = ? AND " +
            "${CalendarContract.CalendarAlerts.STATE} = ?"
        val selectionArgs = arrayOf(
            alarmTime.toString(),
            CalendarContract.CalendarAlerts.STATE_SCHEDULED.toString(),
        )
        // Guard against duplicate alert rows for the same event+time.
        val postedKeys = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val alertId = cursor.getLong(0)
                    val eventId = cursor.getLong(1)
                    val begin = cursor.getLong(4)
                    markAlertDismissed(context, alertId)
                    if (!postedKeys.add(eventId xor begin)) continue
                    postNotification(
                        context = context,
                        eventId = eventId,
                        title = cursor.getString(2).orEmpty(),
                        location = cursor.getString(3),
                        begin = begin,
                        allDay = cursor.getInt(5) != 0,
                    )
                }
            }
        }
    }

    /** Marks the alert as dismissed so it is never re-broadcast into a notification. */
    private fun markAlertDismissed(context: Context, alertId: Long) {
        runCatching {
            val values = ContentValues().apply {
                put(
                    CalendarContract.CalendarAlerts.STATE,
                    CalendarContract.CalendarAlerts.STATE_DISMISSED,
                )
            }
            val uri = ContentUris.withAppendedId(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                alertId,
            )
            context.contentResolver.update(uri, values, null, null)
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
            .setOnlyAlertOnce(true)
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
