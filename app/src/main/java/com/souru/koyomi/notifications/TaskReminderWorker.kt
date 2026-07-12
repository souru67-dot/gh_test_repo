package com.souru.koyomi.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.MainActivity
import com.souru.koyomi.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Posts the reminder notification for a local task. */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.success()
        val app = applicationContext as KoyomiApplication
        val task = app.container.taskRepository.getTask(taskId) ?: return Result.success()
        if (task.done) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EPOCH_DAY, task.dueDate.toEpochDay())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ("task" + task.id).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val timeText = task.time?.format(
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()),
        )
        val notification = NotificationCompat
            .Builder(applicationContext, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(
                listOfNotNull(
                    applicationContext.getString(R.string.tasks),
                    timeText,
                ).joinToString("  "),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(("task" + task.id).hashCode(), notification)
        }
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
