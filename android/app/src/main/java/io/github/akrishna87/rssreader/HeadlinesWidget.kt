package io.github.akrishna87.rssreader

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.akrishna87.rssreader.data.Filters
import io.github.akrishna87.rssreader.data.Store
import java.io.File

/** Home-screen widget: the latest unread headlines. Tapping one opens it in the app. */
class HeadlinesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    companion object {
        private val ROWS = intArrayOf(R.id.w_item1, R.id.w_item2, R.id.w_item3, R.id.w_item4, R.id.w_item5)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HeadlinesWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val snap = Store(File(context.filesDir, "reader.json")).load()
            val filters = Filters(snap.mute, snap.highlight)
            val unread = snap.articles.filter { !it.read && filters.mutedBy(it) == null }
            val shown = unread.take(ROWS.size)

            val views = RemoteViews(context.packageName, R.layout.widget_headlines)
            views.setTextViewText(R.id.w_header, if (unread.isEmpty()) "Vellum" else "Vellum · ${unread.size} unread")
            views.setOnClickPendingIntent(R.id.w_header, open(context, null, 0))
            views.setViewVisibility(R.id.w_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
            ROWS.forEachIndexed { i, row ->
                val a = shown.getOrNull(i)
                if (a == null) {
                    views.setViewVisibility(row, View.GONE)
                } else {
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(row, a.title)
                    views.setOnClickPendingIntent(row, open(context, a.id, i + 1))
                }
            }
            manager.updateAppWidget(ids, views)
        }

        private fun open(context: Context, articleId: String?, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (articleId != null) putExtra(MainActivity.EXTRA_ARTICLE, articleId)
            }
            return PendingIntent.getActivity(context, 100 + code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
