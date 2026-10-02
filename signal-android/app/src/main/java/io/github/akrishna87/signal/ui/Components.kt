package io.github.akrishna87.signal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.signal.Rating
import io.github.akrishna87.signal.Ratings
import io.github.akrishna87.signal.Sample
import io.github.akrishna87.signal.Tech

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
fun RatingDot(rating: Rating?, size: Int = 10) {
    Box(Modifier.size(size.dp).background(ratingColor(rating), CircleShape))
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * A 240° arc that fills from very poor (left) to excellent (right), with the dBm number in the
 * middle.
 */
@Composable
fun Gauge(tech: Tech?, dbm: Int?, rating: Rating?, modifier: Modifier = Modifier) {
    val target = if (tech != null && dbm != null) Ratings.fraction(tech, dbm) else 0f
    val fraction by animateFloatAsState(target, label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = ratingColor(rating)
    Box(
        modifier.size(220.dp).semantics {
            contentDescription = if (dbm != null) "$dbm dBm, ${rating?.label ?: ""}" else "No reading"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 150f, 240f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (dbm != null) {
                drawArc(fill, 150f, 240f * fraction.coerceAtLeast(0.01f), false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dbm?.toString() ?: "–",
                fontSize = 56.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("dBm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rating != null) {
                Text(rating.label, style = MaterialTheme.typography.titleMedium, color = fill, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Strength over the last [windowMillis], with the excellent…very poor bands shaded behind the
 * line. Gaps (no reading, or the app was closed) break the line.
 */
@Composable
fun HistoryGraph(samples: List<Sample>, tech: Tech, nowMillis: Long, windowMillis: Long, modifier: Modifier = Modifier) {
    val range = Ratings.gaugeRange(tech)
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bandColors = Rating.entries.associateWith { ratingColor(it).copy(alpha = 0.10f) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val left = 34.dp.toPx()
        val w = size.width - left
        val h = size.height
        fun y(v: Int) = h * (range.last - v.coerceIn(range.first, range.last)) / (range.last - range.first).toFloat()
        fun x(t: Long) = left + w * (1f - (nowMillis - t).toFloat() / windowMillis)

        // Shaded bands, from the top of the range down.
        var top = range.last
        for (r in Rating.entries) {
            val floor = if (r == Rating.VERY_POOR) range.first else threshold(tech, r).coerceAtLeast(range.first)
            if (floor < top) {
                drawRect(bandColors.getValue(r), Offset(left, y(top)), Size(w, y(floor) - y(top)))
                top = floor
            }
        }
        val step = if (range.last - range.first > 60) 20 else 10
        var v = range.last - (range.last % step + step) % step
        while (v >= range.first) {
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f)
            val text = measurer.measure(v.toString(), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 4.dp.toPx(), yy - text.size.height / 2f))
            v -= step
        }

        val path = Path()
        var pen = false
        var lastT = 0L
        for (s in samples) {
            val d = s.dbm
            if (d == null || s.tech != tech || s.atMillis < nowMillis - windowMillis) { pen = false; continue }
            val px = x(s.atMillis)
            val py = y(d)
            if (pen && s.atMillis - lastT <= 5_000) path.lineTo(px, py) else path.moveTo(px, py)
            pen = true
            lastT = s.atMillis
        }
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        samples.lastOrNull()?.let { s ->
            if (s.dbm != null && s.tech == tech) drawCircle(line, 4.dp.toPx(), Offset(x(s.atMillis), y(s.dbm)))
        }
    }
}

/** The lowest value that still earns [r] for this technology. */
private fun threshold(tech: Tech, r: Rating): Int {
    val range = Ratings.gaugeRange(tech)
    return (range.first..range.last).firstOrNull { Ratings.strength(tech, it).score >= r.score } ?: range.last
}
