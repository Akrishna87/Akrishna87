package io.github.akrishna87.daybook.lock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Settings
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Everything the lock screen shows at one moment. */
class LockSnapshot(
    val now: LocalDateTime,
    /** Daybook's own events plus any from the phone's calendars. */
    val events: List<Event>,
    val tasks: List<Task>,
    val settings: Settings,
    val is24Hour: Boolean,
    /** False on the home screen (unless the user wants it there too): only the background is drawn. */
    val showAgenda: Boolean = true,
)

/**
 * Draws the lock screen: the month, today's plan, the year so far and a Today / Tomorrow / Tasks
 * card, laid out below Android's own clock. Sizes are in "units" of 1/400 of the screen width,
 * so it looks the same on every phone. Used by the live wallpaper and by the preview in Settings.
 */
object LockRenderer {
    private const val TODAY_FILL = 0xFF3F1D73.toInt()
    private const val TODAY_PILL = 0xFFFF3B5C.toInt()
    private const val TOMORROW_PILL = 0xFFF2A03D.toInt()
    private const val SHADOW = 0x55000000

    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    fun draw(canvas: Canvas, width: Int, height: Int, s: LockSnapshot, photo: Bitmap?) {
        drawBackground(canvas, width, height, s.settings, photo)
        if (!s.showAgenda) return
        Layouter(canvas, width, height, s).draw()
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, settings: Settings, photo: Bitmap?) {
        val bg = settings.background
        if (photo != null && settings.photo) {
            canvas.drawColor(bg)
            // Centre-crop the photo to the screen.
            val scale = maxOf(w / photo.width.toFloat(), h / photo.height.toFloat())
            val sw = (w / scale).toInt()
            val sh = (h / scale).toInt()
            val sx = (photo.width - sw) / 2
            val sy = (photo.height - sh) / 2
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), p)
            canvas.drawColor(Color.argb((settings.dim.coerceIn(0f, 0.9f) * 255).toInt(), 0, 0, 0))
        } else {
            val top = ColorUtils.blendARGB(bg, Color.WHITE, 0.10f)
            val bottom = ColorUtils.blendARGB(bg, Color.BLACK, 0.22f)
            val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
    }

    private class Layouter(val c: Canvas, val w: Int, val h: Int, val s: LockSnapshot) {
        val u = w / 400f
        val left = 22 * u
        val right = w - 22 * u
        val today = s.now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val locale: Locale = Locale.getDefault()
        val settings = s.settings
        val todays = Agenda.eventsOn(today, s.events)

        fun paint(size: Float, face: Typeface = regular, color: Int = Color.WHITE, alpha: Int = 255) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * u
                typeface = face
                this.color = color
                this.alpha = alpha
                setShadowLayer(3 * u, 0f, 0.6f * u, SHADOW)
            }

        fun fill(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = alpha }

        fun ellipsize(text: String, p: TextPaint, width: Float) = TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString()

        fun draw() {
            val top = h * settings.topOffset.coerceIn(0.05f, 0.7f)
            // Keep clear of the lock screen's shortcut buttons and the unlock hint at the bottom.
            val bottom = h - 120 * u
            val yearHeight = if (settings.showYear) 54 * u else 0f
            val card = if (settings.showCard) CardContent() else null
            val cardHeight = card?.height ?: 0f
            val upperLimit = bottom - yearHeight - (if (card != null) cardHeight + 18 * u else 0f)

            var y = top
            if (settings.showMonth || settings.showTimeline) {
                val monthBottom = if (settings.showMonth) drawMonth(left, y) else y
                val timelineLeft = if (settings.showMonth) left + 196 * u else left
                val timelineBottom = if (settings.showTimeline) drawTimeline(timelineLeft, y, maxOf(upperLimit, monthBottom)) else y
                y = maxOf(monthBottom, timelineBottom) + 18 * u
            }
            if (settings.showYear) {
                drawYear(y)
                y += yearHeight
            }
            card?.draw(y + 6 * u)
        }

        /** The month grid with today circled and a dot under each day that has something on. */
        fun drawMonth(x: Float, top: Float): Float {
            val cell = 24.5f * u
            val month = YearMonth.from(today)
            val title = paint(15.5f, bold)
            c.drawText(month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale), x, top + 15 * u, title)

            val weekStart = Agenda.weekStart(settings)
            val letters = paint(9.5f, bold, alpha = 230).apply { textAlign = Paint.Align.CENTER }
            Agenda.weekdayLetters(weekStart, locale).forEachIndexed { i, l -> c.drawText(l, x + cell * i + cell / 2, top + 33 * u, letters) }

            val dots = Agenda.eventColorsByDay(s.events, month.atDay(1), month.atEndOfMonth().plusDays(1))
            val number = paint(12f, medium).apply { textAlign = Paint.Align.CENTER }
            val todayNumber = paint(12f, bold).apply { textAlign = Paint.Align.CENTER; clearShadowLayer() }
            val rowHeight = 22 * u
            var rowTop = top + 40 * u
            for (week in Agenda.monthGrid(month, weekStart)) {
                week.forEachIndexed { i, day ->
                    if (day == null) return@forEachIndexed
                    val cx = x + cell * i + cell / 2
                    val cy = rowTop + 10 * u
                    if (day == today) {
                        c.drawCircle(cx, cy, 10.5f * u, fill(TODAY_FILL))
                        c.drawText(day.dayOfMonth.toString(), cx, cy + 4.2f * u, todayNumber)
                    } else {
                        c.drawText(day.dayOfMonth.toString(), cx, cy + 4.2f * u, number)
                    }
                    dots[day]?.let { c.drawCircle(cx, rowTop + 20 * u, 1.6f * u, fill(it)) }
                }
                rowTop += rowHeight
            }
            return rowTop
        }

        /** Today's events, each with a coloured bar, its title and its time. */
        fun drawTimeline(x: Float, top: Float, limit: Float): Float {
            val textX = x + 11 * u
            val width = (right - textX).toInt().coerceAtLeast(1)
            val timePaint = paint(11f, regular, alpha = 235)
            var y = top
            if (todays.isEmpty()) {
                c.drawText("Nothing planned today", x, y + 14 * u, paint(13f, medium, alpha = 220))
                return y + 22 * u
            }
            todays.forEachIndexed { i, e ->
                val past = Agenda.isPast(e, today, s.now)
                val titlePaint = paint(15f, bold, alpha = if (past) 170 else 255)
                val layout = StaticLayout.Builder.obtain(e.title, 0, e.title.length, titlePaint, width)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()
                val block = layout.height + 18 * u
                val remaining = todays.size - i
                if (y + block > limit && i > 0) {
                    c.drawText("+$remaining more", textX, y + 12 * u, paint(11.5f, medium, alpha = 220))
                    return y + 18 * u
                }
                c.drawRoundRect(RectF(x, y + 1 * u, x + 3.5f * u, y + block - 1 * u), 2 * u, 2 * u, fill(e.color, if (past) 170 else 255))
                c.save()
                c.translate(textX, y)
                layout.draw(c)
                c.restore()
                val timeY = y + layout.height + 3 * u
                timePaint.alpha = if (past) 170 else 235
                drawClock(textX + 5 * u, timeY + 7 * u, 4.6f * u, timePaint.alpha)
                c.drawText(Agenda.timeLabel(e, today, s.is24Hour, locale), textX + 14 * u, timeY + 11 * u, timePaint)
                y += block + 11 * u
            }
            return y - 11 * u
        }

        fun drawClock(cx: Float, cy: Float, r: Float, alpha: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.alpha = alpha
                style = Paint.Style.STROKE
                strokeWidth = 1.1f * u
                strokeCap = Paint.Cap.ROUND
            }
            c.drawCircle(cx, cy, r, p)
            c.drawLine(cx, cy, cx, cy - r * 0.55f, p)
            c.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.2f, p)
        }

        /** "2026", a progress line and how much of the year is left. */
        fun drawYear(top: Float) {
            val percent = Agenda.yearPercent(today)
            c.drawText(today.year.toString(), left, top + 16 * u, paint(17f, bold))
            c.drawText("$percent%", right, top + 16 * u, paint(18f, bold).apply { textAlign = Paint.Align.RIGHT })
            val barY = top + 24 * u
            val bar = 3f * u
            c.drawRoundRect(RectF(left, barY, right, barY + bar), bar, bar, fill(Color.WHITE, 90))
            c.drawRoundRect(RectF(left, barY, left + (right - left) * percent / 100f, barY + bar), bar, bar, fill(Color.WHITE))
            val small = paint(11f, regular, alpha = 235)
            c.drawText("$percent% of this year has passed", left, top + 42 * u, small)
            val days = Agenda.daysLeft(today)
            c.drawText(if (days == 1) "1 day left" else "$days days left", right, top + 50 * u, small.apply { textAlign = Paint.Align.RIGHT })
        }

        /** The glass card: what's next today and tomorrow on the left, tasks on the right. */
        inner class CardContent {
            val pad = 16 * u
            val upcoming = todays.filter { !Agenda.isPast(it, today, s.now) }
            val tomorrows = Agenda.eventsOn(tomorrow, s.events)
            val tasks = Agenda.openTasks(s.tasks)
            val todayShown = upcoming.take(2)
            val tomorrowShown = tomorrows.take(if (todayShown.size < 2) 2 else 1)
            val tasksShown = tasks.take(5)
            val rowHeight = 31 * u
            val leftHeight = 20 * u + maxOf(1, todayShown.size) * rowHeight + 8 * u + 20 * u + maxOf(1, tomorrowShown.size) * rowHeight
            val rightHeight = 20 * u + maxOf(1, tasksShown.size) * 21 * u + (if (tasks.size > tasksShown.size) 18 * u else 0f)
            val height = pad * 2 + maxOf(leftHeight, rightHeight)

            fun draw(top: Float) {
                val box = RectF(left, top, right, top + height)
                c.drawRoundRect(box, 24 * u, 24 * u, fill(Color.WHITE, 46))
                c.drawRoundRect(box, 24 * u, 24 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    alpha = 70
                    style = Paint.Style.STROKE
                    strokeWidth = 1f * u
                })
                val mid = left + (right - left) * 0.52f
                val colLeft = left + pad
                var y = top + pad
                y = section("Today", TODAY_PILL, upcoming.size - todayShown.size, todayShown, today, colLeft, y, mid - 10 * u, "Nothing else today")
                y += 8 * u
                section("Tomorrow", TOMORROW_PILL, tomorrows.size - tomorrowShown.size, tomorrowShown, tomorrow, colLeft, y, mid - 10 * u, "Nothing planned")

                var ty = top + pad
                c.drawText("Tasks", mid, ty + 11 * u, paint(12f, bold))
                ty += 20 * u
                val taskPaint = paint(12f, medium)
                if (tasksShown.isEmpty()) {
                    c.drawText("All done", mid, ty + 11 * u, paint(12f, medium, alpha = 200))
                }
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * u }
                for (t in tasksShown) {
                    boxPaint.color = t.color
                    c.drawRoundRect(RectF(mid, ty + 1.5f * u, mid + 11 * u, ty + 12.5f * u), 2.8f * u, 2.8f * u, boxPaint)
                    c.drawText(ellipsize(t.title, taskPaint, right - pad - mid - 18 * u), mid + 18 * u, ty + 11.5f * u, taskPaint)
                    ty += 21 * u
                }
                if (tasks.size > tasksShown.size) c.drawText("+${tasks.size - tasksShown.size} more", mid + 18 * u, ty + 11 * u, paint(10.5f, medium, alpha = 210))
            }

            fun section(label: String, pill: Int, more: Int, events: List<Event>, day: java.time.LocalDate, x: Float, top: Float, maxX: Float, empty: String): Float {
                val pillText = paint(10f, bold).apply { clearShadowLayer() }
                val pw = pillText.measureText(label) + 14 * u
                c.drawRoundRect(RectF(x, top, x + pw, top + 16 * u), 8 * u, 8 * u, fill(pill))
                c.drawText(label, x + 7 * u, top + 11.5f * u, pillText)
                if (more > 0) c.drawText("+$more more", x + pw + 6 * u, top + 11.5f * u, paint(10f, medium, alpha = 220))
                var y = top + 20 * u
                if (events.isEmpty()) {
                    c.drawText(empty, x, y + 13 * u, paint(11.5f, medium, alpha = 200))
                    return y + rowHeight
                }
                val titlePaint = paint(12.5f, bold)
                val timePaint = paint(10.5f, regular, alpha = 230)
                for (e in events) {
                    c.drawRoundRect(RectF(x, y + 2 * u, x + 3 * u, y + 27 * u), 1.5f * u, 1.5f * u, fill(e.color))
                    c.drawText(ellipsize(e.title, titlePaint, maxX - x - 9 * u), x + 9 * u, y + 12.5f * u, titlePaint)
                    c.drawText(Agenda.timeLabel(e, day, s.is24Hour, locale), x + 9 * u, y + 25 * u, timePaint)
                    y += rowHeight
                }
                return y
            }
        }
    }
}
