package io.github.akrishna87.daybook

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Notifications at a task's due time. Alarms are kept in step with the tasks every time they change. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    const val ACTION_REMIND = "io.github.akrishna87.daybook.REMIND"
    const val ACTION_COMPLETE = "io.github.akrishna87.daybook.COMPLETE"
    const val EXTRA_TASK = "task"

    fun sync(context: Context, data: Data) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val wanted: Map<String, Long> = if (!data.settings.reminders) {
            emptyMap()
        } else {
            data.tasks.mapNotNull { t ->
                val at = Dates.reminderAt(t)?.takeIf { it.isAfter(now) && it.isBefore(now.plusDays(90)) } ?: return@mapNotNull null
                t.id to at.atZone(zone).toInstant().toEpochMilli()
            }.toMap()
        }
        for (id in before - wanted.keys) alarms.cancel(alarm(context, id))
        for ((id, at) in wanted) {
            runCatching {
                if (canScheduleExact(context)) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id))
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id))
                }
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, wanted.keys).apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun alarm(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("daybook://task/$id"))
            .putExtra(EXTRA_TASK, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, task: Task, data: Data) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A notification when a task with a time is due"
                },
            )
        }
        val today = LocalDate.now()
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val project = data.project(task.projectId).name
        val text = listOfNotNull(Dates.dueLabel(task, today, is24), project).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context, task.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("daybook://task/${task.id}"))
                .putExtra(MainActivity.EXTRA_OPEN_TASK, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val complete = PendingIntent.getBroadcast(
            context, task.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_COMPLETE)
                .setData(Uri.parse("daybook://task/${task.id}"))
                .putExtra(EXTRA_TASK, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_daybook)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (task.description.isBlank()) text else "$text\n${task.description}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Complete", complete)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification) }
    }
}

/** Shows due reminders, completes tasks from the notification, and puts alarms back after a restart. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_TASK)
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                val d = Store.data.value
                val task = d.task(id) ?: return
                if (!task.done && d.settings.reminders) Reminders.show(context, task, d)
            }
            Reminders.ACTION_COMPLETE -> {
                if (id == null) return
                Store.completeTask(id)
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            else -> Reminders.sync(context, Store.data.value)
        }
    }
}
