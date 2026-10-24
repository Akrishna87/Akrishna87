package io.github.akrishna87.rssreader

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed

object Notifier {
    private const val CHANNEL = "new_articles"

    fun show(context: Context, articles: List<Article>, feeds: List<Feed>) {
        if (articles.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("New articles").build(),
        )
        val names = feeds.associate { it.id to it.name }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (articles.size == 1) putExtra(MainActivity.EXTRA_ARTICLE, articles[0].id)
        }
        val tap = PendingIntent.getActivity(context, articles.size, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_feed)
            .setColor(0xFF2BB24C.toInt())
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (articles.size == 1) {
            val a = articles[0]
            builder.setContentTitle(a.title).setContentText(names[a.feedId].orEmpty())
        } else {
            val style = NotificationCompat.InboxStyle()
            articles.take(5).forEach { style.addLine(it.title) }
            builder.setContentTitle("${articles.size} new articles")
                .setContentText(articles.take(3).joinToString(" · ") { names[it.feedId].orEmpty() }.take(80))
                .setStyle(style)
        }
        manager.notify(1, builder.build())
    }
}
