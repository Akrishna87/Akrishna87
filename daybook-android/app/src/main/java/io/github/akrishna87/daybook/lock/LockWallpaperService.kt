package io.github.akrishna87.daybook.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import io.github.akrishna87.daybook.DeviceCalendar
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Daybook's lock screen. Android doesn't let apps put widgets under the lock screen clock, but a
 * live wallpaper is drawn there, so this wallpaper draws the calendar, today's plan, the year so
 * far and the tasks card. On the home screen it shows just the background, so it doesn't clash
 * with your icons (unless you ask for the layout there too).
 */
class LockWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LockEngine()

    inner class LockEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
        private var visible = false
        private var width = 0
        private var height = 0
        private var photo: Bitmap? = null
        private var photoStamp = 0L
        private var deviceEvents: List<Event> = emptyList()
        private var deviceLoadedAt = 0L

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    // Once a minute is enough to fade finished events, but only while someone can see it.
                    Intent.ACTION_TIME_TICK -> if (!visible) return
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> deviceLoadedAt = 0L
                }
                refresh()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Store.init(applicationContext)
            setTouchEventsEnabled(false)
            val filter = IntentFilter().apply {
                // Screen off: get the layout ready for the lock screen. Unlocked: back to the plain background.
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(this@LockWallpaperService, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            // Redraw whenever an event, task or setting changes, in the app or the widget.
            scope.launch { Store.data.drop(1).collect { deviceLoadedAt = 0L; refresh() } }
        }

        override fun onDestroy() {
            scope.cancel()
            runCatching { unregisterReceiver(receiver) }
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) refresh()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width != this.width || height != this.height) photo = null
            this.width = width
            this.height = height
            refresh()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            super.onSurfaceDestroyed(holder)
        }

        private fun refresh() {
            scope.launch {
                val settings = Store.data.value.settings
                if (!settings.deviceCalendars) {
                    deviceEvents = emptyList()
                } else if (System.currentTimeMillis() - deviceLoadedAt > 10 * 60_000L) {
                    deviceLoadedAt = System.currentTimeMillis()
                    val first = LocalDate.now().withDayOfMonth(1)
                    deviceEvents = withContext(Dispatchers.IO) {
                        DeviceCalendar.events(applicationContext, first, first.plusMonths(1).plusDays(2))
                    }
                }
                if (!settings.photo) {
                    photo = null
                } else {
                    val stamp = LockPhoto.file(applicationContext).lastModified()
                    if (photo == null || stamp != photoStamp) {
                        photoStamp = stamp
                        photo = withContext(Dispatchers.IO) { LockPhoto.load(applicationContext, width, height) }
                    }
                }
                draw()
            }
        }

        private fun draw() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val d = Store.data.value
                val locked = runCatching { keyguard.isKeyguardLocked }.getOrDefault(true)
                LockRenderer.draw(
                    canvas, width, height,
                    LockSnapshot(
                        now = LocalDateTime.now(),
                        events = d.events + deviceEvents,
                        tasks = d.tasks,
                        settings = d.settings,
                        is24Hour = DateFormat.is24HourFormat(applicationContext),
                        showAgenda = locked || isPreview || d.settings.onHomeScreen,
                    ),
                    photo,
                )
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
