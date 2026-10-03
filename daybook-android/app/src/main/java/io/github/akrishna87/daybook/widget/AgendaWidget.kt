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
import io.github.akrishna87.daybook.DeviceCalendar
import io.github.akrishna87.daybook.MainActivity
import io.github.akrishna87.daybook.R
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Event
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The home screen card: what's next today and tomorrow, and the tasks still to do. Tap a task to tick it off. */
class AgendaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                Store.init(context)
                // Store.update refreshes every widget.
                intent.getStringExtra(EXTRA_TASK)?.let(Store::toggleTask)
            }
            ACTION_MIDNIGHT -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "io.github.akrishna87.daybook.TOGGLE_TASK"
        private const val ACTION_MIDNIGHT = "io.github.akrishna87.daybook.MIDNIGHT"
        private const val EXTRA_TASK = "task"
        private const val TODAY_ROWS = 2
        private const val TASK_ROWS = 4

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AgendaWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            Store.init(context)
            val d = Store.data.value
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val tomorrow = today.plusDays(1)
            val events = d.events + DeviceCalendar.eventsIfEnabled(context, d.settings.deviceCalendars, today, today.plusDays(2))
            val is24 = DateFormat.is24HourFormat(context)

            val upcoming = Agenda.eventsOn(today, events).filter { !Agenda.isPast(it, today, now) }
            val tomorrows = Agenda.eventsOn(tomorrow, events)
            val tasks = Agenda.openTasks(d.tasks)

            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

            fillEvents(context, views, R.id.today_list, R.id.today_more, upcoming, TODAY_ROWS, today, is24, "Nothing else today")
            val tomorrowRows = if (upcoming.size < TODAY_ROWS) 2 else 1
            fillEvents(context, views, R.id.tomorrow_list, R.id.tomorrow_more, tomorrows, tomorrowRows, tomorrow, is24, "Nothing planned")

            views.removeAllViews(R.id.tasks_list)
            if (tasks.isEmpty()) views.addView(R.id.tasks_list, emptyRow(context, "All done"))
            for (t in tasks.take(TASK_ROWS)) {
                val row = RemoteViews(context.packageName, R.layout.widget_task_row)
                row.setTextViewText(R.id.task_title, t.title)
                row.setInt(R.id.task_box, "setColorFilter", t.color)
                row.setOnClickPendingIntent(R.id.task_row, toggle(context, t.id))
                views.addView(R.id.tasks_list, row)
            }
            more(views, R.id.tasks_more, tasks.size - TASK_ROWS)

            manager.updateAppWidget(ids, views)
            scheduleMidnight(context)
        }

        private fun fillEvents(
            context: Context, views: RemoteViews, list: Int, moreId: Int,
            events: List<Event>, rows: Int, day: LocalDate, is24: Boolean, empty: String,
        ) {
            views.removeAllViews(list)
            if (events.isEmpty()) views.addView(list, emptyRow(context, empty))
            for (e in events.take(rows)) {
                val row = RemoteViews(context.packageName, R.layout.widget_event_row)
                row.setTextViewText(R.id.event_title, e.title)
                row.setTextViewText(R.id.event_time, Agenda.timeLabel(e, day, is24))
                row.setInt(R.id.event_bar, "setBackgroundColor", e.color)
                views.addView(list, row)
            }
            more(views, moreId, events.size - rows)
        }

        private fun more(views: RemoteViews, id: Int, count: Int) {
            if (count > 0) {
                views.setTextViewText(id, "+$count more")
                views.setViewVisibility(id, View.VISIBLE)
            } else {
                views.setViewVisibility(id, View.GONE)
            }
        }

        private fun emptyRow(context: Context, text: String) =
            RemoteViews(context.packageName, R.layout.widget_empty_row).apply { setTextViewText(R.id.empty_text, text) }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun toggle(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(),
            Intent(context, AgendaWidget::class.java)
                .setAction(ACTION_TOGGLE)
                .setData(Uri.parse("daybook://task/$taskId"))
                .putExtra(EXTRA_TASK, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Moves "Tomorrow" up to "Today" soon after midnight. */
        private fun scheduleMidnight(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
            val intent = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, AgendaWidget::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarms.set(AlarmManager.RTC, at, intent)
        }
    }
}
