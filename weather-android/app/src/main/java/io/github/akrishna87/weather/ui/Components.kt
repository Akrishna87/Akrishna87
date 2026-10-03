package io.github.akrishna87.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ---- Sky backgrounds for the Weather screen ----

private val CLEAR_DAY = listOf(Color(0xFF0B5FC9), Color(0xFF2F86F0), Color(0xFF7DB8F7))
private val CLOUDY_DAY = listOf(Color(0xFF3F556E), Color(0xFF627A93), Color(0xFF94A7BA))
private val RAIN_DAY = listOf(Color(0xFF243447), Color(0xFF3B5268), Color(0xFF5E758B))
private val STORM = listOf(Color(0xFF17142A), Color(0xFF2F2A4D), Color(0xFF4F4677))
private val SNOW_DAY = listOf(Color(0xFF5D79A8), Color(0xFF8AA4CB), Color(0xFFB7C7E0))
private val FOG = listOf(Color(0xFF4D5A68), Color(0xFF75828F), Color(0xFFA0AAB4))
private val CLEAR_NIGHT = listOf(Color(0xFF070B1F), Color(0xFF15214A), Color(0xFF2A3D73))
private val CLOUDY_NIGHT = listOf(Color(0xFF0F131B), Color(0xFF212938), Color(0xFF364256))

/** The sky behind the Weather screen: it follows the current weather and whether it's day or night. */
fun skyBrush(code: Int?, isDay: Boolean): Brush = Brush.verticalGradient(
    when (code) {
        95, 96, 99 -> STORM
        71, 73, 75, 77, 85, 86 -> if (isDay) SNOW_DAY else CLOUDY_NIGHT
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> if (isDay) RAIN_DAY else CLOUDY_NIGHT
        45, 48 -> FOG
        3 -> if (isDay) CLOUDY_DAY else CLOUDY_NIGHT
        else -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
    },
)

/** Text on the sky: white, and a softer white for secondary text. */
val OnSky = Color.White
val OnSkyDim = Color.White.copy(alpha = 0.72f)
val RainBlue = Color(0xFF9BD1FF)

/** A frosted-glass panel for content over the sky. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    alpha: Float = 0.13f,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = alpha))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** A small capitalised heading with an icon, as on the cards of most weather apps. */
@Composable
fun SectionLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}

/** A rounded label, e.g. a storm's category. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else color,
        )
    }
}

/** A labelled figure: "Humidity / 86%". */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, sub: String? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.7f))
    }
}

// ---- Temperature colours (for the 7-day range bars) ----

private val TEMP_STOPS = floatArrayOf(-15f, 0f, 10f, 18f, 25f, 32f, 40f)
private val TEMP_COLORS = listOf(
    Color(0xFF818CF8), Color(0xFF60A5FA), Color(0xFF22D3EE), Color(0xFF4ADE80),
    Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFEF4444),
)

fun tempColor(c: Float): Color {
    if (c <= TEMP_STOPS.first()) return TEMP_COLORS.first()
    for (i in 1 until TEMP_STOPS.size) {
        if (c <= TEMP_STOPS[i]) {
            val t = (c - TEMP_STOPS[i - 1]) / (TEMP_STOPS[i] - TEMP_STOPS[i - 1])
            return androidx.compose.ui.graphics.lerp(TEMP_COLORS[i - 1], TEMP_COLORS[i], t)
        }
    }
    return TEMP_COLORS.last()
}

/**
 * A day's low-to-high bar on the week's scale ([weekMin]..[weekMax]), coloured by temperature,
 * with an optional dot for the temperature right now.
 */
@Composable
fun RangeBar(min: Double, max: Double, weekMin: Double, weekMax: Double, now: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier.height(6.dp)) {
        val span = (weekMax - weekMin).coerceAtLeast(1.0)
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = r)
        val a = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width
        val b = ((max - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(min.toFloat()), tempColor(max.toFloat())), startX = a, endX = b),
            topLeft = Offset(a, 0f),
            size = Size((b - a).coerceAtLeast(size.height), size.height),
            cornerRadius = r,
        )
        if (now != null) {
            val x = ((now - weekMin) / span).toFloat().coerceIn(0f, 1f) * size.width
            drawCircle(Color(0xFF0B1220), radius = size.height * 0.95f, center = Offset(x, size.height / 2))
            drawCircle(Color.White, radius = size.height * 0.65f, center = Offset(x, size.height / 2))
        }
    }
}

/**
 * A compass showing which way the wind blows (the arrow points where it's going), with the
 * speed in the middle.
 */
@Composable
fun CompassDial(fromDeg: Double?, speed: String, unit: String, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val measurer = rememberTextMeasurer()
    val letter = TextStyle(color = OnSkyDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val north = letter.copy(color = Color(0xFFFF8A80))
    val big = TextStyle(color = OnSky, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(color = OnSkyDim, fontSize = 11.sp)
    Canvas(modifier.size(size)) {
        val c = center
        val radius = this.size.minDimension / 2
        // Ticks every 10°, longer every 30°.
        for (deg in 0 until 360 step 10) {
            val rad = Math.toRadians(deg.toDouble() - 90)
            val long = deg % 30 == 0
            val r1 = radius - (if (long) 9.dp.toPx() else 5.dp.toPx())
            drawLine(
                Color.White.copy(alpha = if (long) 0.6f else 0.3f),
                Offset(c.x + (r1 * cos(rad)).toFloat(), c.y + (r1 * sin(rad)).toFloat()),
                Offset(c.x + (radius * cos(rad)).toFloat(), c.y + (radius * sin(rad)).toFloat()),
                strokeWidth = (if (long) 1.5f else 1f).dp.toPx(),
            )
        }
        // Compass letters.
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (s, deg) ->
            val layout = measurer.measure(s, if (s == "N") north else letter)
            val rad = Math.toRadians(deg.toDouble() - 90)
            val rr = radius - 19.dp.toPx()
            drawText(
                layout,
                topLeft = Offset(
                    c.x + (rr * cos(rad)).toFloat() - layout.size.width / 2,
                    c.y + (rr * sin(rad)).toFloat() - layout.size.height / 2,
                ),
            )
        }
        // The arrow, pointing where the wind is going.
        if (fromDeg != null) {
            rotate((fromDeg + 180).toFloat(), c) {
                val tip = Offset(c.x, c.y - radius + 12.dp.toPx())
                val tail = Offset(c.x, c.y + radius - 12.dp.toPx())
                val stroke = 2.5.dp.toPx()
                drawLine(Color.White, Offset(c.x, c.y - radius * 0.42f), tip, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x, c.y + radius * 0.42f), tail, strokeWidth = stroke, cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 2.dp.toPx())
                    lineTo(tip.x - 6.dp.toPx(), tip.y + 9.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y + 9.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
                drawCircle(Color.White.copy(alpha = 0.7f), radius = 3.dp.toPx(), center = tail)
            }
        }
        // Speed in the middle.
        drawCircle(Color.White.copy(alpha = 0.10f), radius = radius * 0.40f, center = c)
        drawCircle(Color.White.copy(alpha = 0.25f), radius = radius * 0.40f, center = c, style = Stroke(1.dp.toPx()))
        val v = measurer.measure(speed, big)
        val u = measurer.measure(unit, small)
        val top = c.y - (v.size.height + u.size.height) / 2f
        drawText(v, topLeft = Offset(c.x - v.size.width / 2f, top))
        drawText(u, topLeft = Offset(c.x - u.size.width / 2f, top + v.size.height - 4.dp.toPx()))
    }
}

/** A colour scale with a marker, e.g. the air-quality index on 0..300. */
@Composable
fun GaugeBar(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(Brush.horizontalGradient(colors), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
        val x = fraction.coerceIn(0f, 1f) * size.width
        drawCircle(Color(0xFF0B1220), radius = size.height / 2 + 1.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height / 2 - 1.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

/** Short storm-category tag: "CAT 3", "TS" (tropical storm), "TD" (depression). */
fun categoryTag(category: Int?): String = when (category) {
    null -> "TC"
    -1 -> "TD"
    0 -> "TS"
    else -> "CAT $category"
}
