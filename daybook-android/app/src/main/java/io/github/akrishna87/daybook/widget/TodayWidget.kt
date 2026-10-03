package io.github.akrishna87.daybook.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import io.github.akrishna87.daybook.MainActivity
import io.github.akrishna87.daybook.R
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The home screen widget: today's and overdue tasks. Tap a circle to complete a task, or + to add one. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> {
                Store.init(context)
                // Store.update refreshes every widget.
                intent.getStringExtra(EXTRA_TASK)?.let { Store.completeTask(it) }
            }
            ACTION_MIDNIGHT -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_COMPLETE = "io.github.akrishna87.daybook.WIDGET_COMPLETE"
        private const val ACTION_MIDNIGHT = "io.github.akrishna87.daybook.WIDGET_MIDNIGHT"
        private const val EXTRA_TASK = "task"
        private const val ROWS = 6

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Store.init(context)
            val d = Store.data.value
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val is24 = DateFormat.is24HourFormat(context)
            val tasks = Dates.overdue(d.tasks, today) + Dates.dueOn(d.tasks, today)

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_date, DateTimeFormatter.ofPattern("EEE d MMM").format(today))
            views.setTextViewText(R.id.widget_count, if (tasks.isEmpty()) "" else tasks.size.toString())
            views.setOnClickPendingIntent(R.id.widget_header, openApp(context, quickAdd = false))
            views.setOnClickPendingIntent(R.id.widget_add, openApp(context, quickAdd = true))

            views.removeAllViews(R.id.widget_list)
            if (tasks.isEmpty()) {
                views.addView(R.id.widget_list, RemoteViews(context.packageName, R.layout.widget_empty))
            }
            for (t in tasks.take(ROWS)) {
                val row = RemoteViews(context.packageName, R.layout.widget_task)
                row.setTextViewText(R.id.task_title, t.title)
                row.setInt(R.id.task_check, "setColorFilter", Priority.color(t.priority))
                val meta = listOfNotNull(
                    Dates.dueLabel(t, today, is24).takeIf { t.time != null || t.due != today },
                    d.project(t.projectId).name.takeIf { t.projectId != io.github.akrishna87.daybook.model.INBOX_ID },
                ).joinToString(" · ")
                row.setTextViewText(R.id.task_meta, meta)
                row.setViewVisibility(R.id.task_meta, if (meta.isEmpty()) View.GONE else View.VISIBLE)
                row.setTextColor(R.id.task_meta, if (Dates.isOverdue(t, now)) 0xFFD1453B.toInt() else 0xFF8A8C99.toInt())
                row.setOnClickPendingIntent(R.id.task_check, complete(context, t.id))
                row.setOnClickPendingIntent(R.id.task_row, openTask(context, t.id))
                views.addView(R.id.widget_list, row)
            }
            if (tasks.size > ROWS) {
                val more = RemoteViews(context.packageName, R.layout.widget_empty)
                more.setTextViewText(R.id.widget_empty_text, "+${tasks.size - ROWS} more")
                views.addView(R.id.widget_list, more)
            }

            manager.updateAppWidget(ids, views)
            scheduleMidnight(context)
        }

        private fun openApp(context: Context, quickAdd: Boolean): PendingIntent = PendingIntent.getActivity(
            context, if (quickAdd) 1 else 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_QUICK_ADD, quickAdd)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun openTask(context: Context, id: String): PendingIntent = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("daybook://widget-task/$id"))
                .putExtra(MainActivity.EXTRA_OPEN_TASK, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun complete(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, TodayWidget::class.java)
                .setAction(ACTION_COMPLETE)
                .setData(Uri.parse("daybook://widget-complete/$id"))
                .putExtra(EXTRA_TASK, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Moves tomorrow's tasks into the widget soon after midnight. */
        private fun scheduleMidnight(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
            val intent = PendingIntent.getBroadcast(
                context, 2,
                Intent(context, TodayWidget::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.set(AlarmManager.RTC, at, intent)
        }
    }
}
