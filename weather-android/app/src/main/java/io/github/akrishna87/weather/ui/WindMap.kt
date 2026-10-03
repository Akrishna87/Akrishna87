package io.github.akrishna87.weather.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.akrishna87.weather.MapCamera
import io.github.akrishna87.weather.WeatherViewModel
import io.github.akrishna87.weather.data.GeoBox
import io.github.akrishna87.weather.data.LatLon
import io.github.akrishna87.weather.data.Storm
import io.github.akrishna87.weather.data.Units
import io.github.akrishna87.weather.data.WindField
import io.github.akrishna87.weather.data.WorldOutline
import io.github.akrishna87.weather.data.degToCompass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The wind-flow map: thousands of particles drifting with the wind (coloured by speed) over a
 * plain world map, with named storms, their tracks and wind areas on top. Pinch to zoom, drag
 * to pan, tap a storm for details or anywhere else for the wind there. The slider steps through
 * the next 48 hours of forecast wind.
 */
@OptIn(FlowPreview::class)
@Composable
fun WindMapScreen(vm: WeatherViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val cam = vm.camera
    val wind by vm.wind.collectAsState()
    val stormsState by vm.storms.collectAsState()
    val place by vm.place.collectAsState()
    val units by vm.units.collectAsState()
    val focus by vm.focus.collectAsState()

    var size by remember { mutableStateOf(IntSize.Zero) }
    var world by remember { mutableStateOf<WorldOutline?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var probe by remember { mutableStateOf<Pair<LatLon, FloatArray?>?>(null) }
    val frame = remember { mutableLongStateOf(0L) }
    val particles = remember { Particles(density) }
    val painter = remember { MapPainter(density) }

    LaunchedEffect(Unit) {
        vm.refreshStormsIfNeeded()
        world = withContext(Dispatchers.IO) { WorldOutline.load(context) }
    }

    // First layout: centre on your place (or the tropics) at a regional zoom.
    LaunchedEffect(size) {
        if (size.width > 0 && cam.ppd == 0f) {
            val p = place
            cam.lon = p?.lon ?: 80.0
            cam.lat = p?.lat ?: 15.0
            cam.ppd = size.width / 60f
        }
    }

    // Fly to a storm or place when asked (once per request).
    LaunchedEffect(focus?.seq, size) {
        val f = focus ?: return@LaunchedEffect
        if (size.width == 0 || f.seq == cam.handledFocus) return@LaunchedEffect
        cam.handledFocus = f.seq
        cam.lon = f.lon
        cam.lat = f.lat
        cam.ppd = (size.width / f.spanDeg).toFloat()
        selected = f.stormKey
        particles.clear()
    }

    // Load the wind for whatever is on screen once the map stops moving.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(cam.lon, cam.lat, cam.ppd) to size }
            .distinctUntilChanged()
            .debounce(600)
            .collect { (c, s) ->
                if (s.width > 0 && c.third > 0f) vm.requestWind(viewBox(cam, s))
            }
    }

    // Animation loop: step the particles, then redraw.
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameMillis { t ->
                val dt = if (last == 0L) 16f else (t - last).coerceIn(5, 50).toFloat()
                last = t
                val f = wind.field
                if (f != null && size.width > 0 && cam.ppd > 0f) {
                    particles.step(f, hourIndex(cam, f), cam.gusts, viewBox(cam, size), cam.ppd, size, dt / 16f)
                }
                frame.longValue = t
            }
        }
    }

    val field = wind.field
    val storms = if (cam.showStorms) stormsState.storms else emptyList()

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1A2A))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (cam.ppd <= 0f) return@detectTransformGestures
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // Keep the point under the fingers still while zooming.
                        val lonAt = cam.lon + (centroid.x - w / 2) / cam.ppd
                        val latAt = cam.lat - (centroid.y - h / 2) / cam.ppd
                        val newPpd = (cam.ppd * zoom).coerceIn(w / 360f, w / 3f)
                        cam.ppd = newPpd
                        cam.lon = lonAt - (centroid.x - w / 2) / newPpd - pan.x / newPpd
                        cam.lat = (latAt + (centroid.y - h / 2) / newPpd + pan.y / newPpd).coerceIn(-80.0, 80.0)
                        if (abs(zoom - 1f) > 0.002f) particles.clear()
                    }
                }
                .pointerInput(storms) {
                    detectTapGestures { pos ->
                        val hit = painter.stormAt(pos, storms, cam, size)
                        if (hit != null) {
                            selected = hit.key
                            probe = null
                        } else {
                            selected = null
                            val ll = toLatLon(pos, cam, size)
                            val out = FloatArray(3)
                            val f = wind.field
                            probe = ll to (if (f != null && f.sample(hourIndex(cam, f), ll.lon, ll.lat, false, out)) out else null)
                        }
                    }
                },
        ) {
            frame.longValue // redraw every frame
            if (cam.ppd <= 0f) return@Canvas
            painter.drawBase(this, cam, world)
            if (field != null) painter.drawHeat(this, cam, field, hourIndex(cam, field), cam.gusts)
            painter.drawCoasts(this, cam, world)
            particles.draw(this, cam)
            painter.drawStorms(this, cam, storms, selected)
            place?.let { painter.drawPlace(this, cam, LatLon(it.lat, it.lon), it.name) }
            probe?.let { painter.drawProbe(this, cam, it.first) }
        }

        // ---- Controls ----
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !cam.gusts, onClick = { cam.gusts = false }, label = { Text("Wind") })
                FilterChip(selected = cam.gusts, onClick = { cam.gusts = true }, label = { Text("Gusts") })
                FilterChip(
                    selected = cam.showStorms,
                    onClick = { cam.showStorms = !cam.showStorms },
                    label = { Text("Storms") },
                    leadingIcon = { Icon(Icons.Outlined.Cyclone, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.weight(1f))
                if (place != null) {
                    MapButton(onClick = { vm.showPlaceOnMap() }) { Icon(Icons.Outlined.MyLocation, "Go to my place") }
                }
                MapButton(onClick = {
                    if (size.width > 0) vm.requestWind(viewBox(cam, size), force = true)
                    vm.refreshStorms()
                }) { Icon(Icons.Outlined.Refresh, "Reload wind and storms") }
            }
            if (wind.loading || stormsState.loading) {
                Pill { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("  Loading ${if (wind.loading) "wind" else "storms"}…") }
            }
            wind.error?.let { Pill { Text("Wind: $it") } }
            if (field == null && !wind.loading && wind.error == null) Pill { Text("Waiting for wind data…") }
            probe?.let { (ll, w) ->
                Pill {
                    Text(
                        "%.1f°%s %.1f°%s · ".format(abs(ll.lat), if (ll.lat >= 0) "N" else "S", abs(normLonDisplay(ll.lon)), if (normLonDisplay(ll.lon) >= 0) "E" else "W") +
                            if (w == null) "no wind data here" else {
                                val from = (Math.toDegrees(kotlin.math.atan2(-w[0].toDouble(), -w[1].toDouble())) + 360) % 360
                                "${units.wind(w[2].toDouble())} from the ${degToCompass(from)}"
                            },
                    )
                    IconButton(onClick = { probe = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, "Close", Modifier.size(16.dp)) }
                }
            }
            if (cam.showStorms && !stormsState.loading && stormsState.error == null && stormsState.storms.isEmpty()) {
                Pill { Text("No named storms are active anywhere right now") }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
            val s = storms.firstOrNull { it.key == selected }
            if (s != null) StormSheet(s, units, place?.let { LatLon(it.lat, it.lon) }) { selected = null }
            if (field != null && field.times.size > 1) TimeSlider(cam, field)
            Legend(units, cam.gusts)
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("EEE HH:mm")

@Composable
private fun TimeSlider(cam: MapCamera, field: WindField) {
    val now = field.nowIndex()
    val idx = hourIndex(cam, field)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val t = Instant.ofEpochMilli(field.times[idx]).atZone(ZoneId.systemDefault())
                val rel = idx - now
                Text(
                    if (rel == 0) "Now · ${t.format(TIME)}" else "${if (rel > 0) "+" else ""}$rel h · ${t.format(TIME)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (rel != 0) TextButton(onClick = { cam.hour = -1 }) { Text("Now") }
            }
            Slider(
                value = idx.toFloat(),
                onValueChange = { cam.hour = it.roundToInt() },
                valueRange = 0f..(field.times.size - 1).toFloat(),
                steps = (field.times.size - 2).coerceAtLeast(0),
                modifier = Modifier.height(32.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Legend(units: Units, gusts: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).background(
                    Brush.horizontalGradient(WIND_COLORS.map { Color(it) }),
                    RoundedCornerShape(4.dp),
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0f, 20f, 45f, 90f, 160f).forEach {
                    Text(units.windNumber(it.toDouble()), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "${if (gusts) "Gusts" else "Wind at 10 m"} (${units.wind.label}) · tap the map for the wind at a spot",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StormSheet(s: Storm, units: Units, you: LatLon?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cyclone, null, tint = Color(stormColor(s.category)), modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(s.kind, style = MaterialTheme.typography.bodyMedium, color = Color(stormColor(s.category)))
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
            StormFacts(s, units, you)
            s.link?.let { url ->
                TextButton(onClick = { openLink(ctx, url) }) { Text("Official advisory (${s.sources.joinToString(" + ")}) →") }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MapButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelMedium) { content() }
        }
    }
}

// ---- Geometry: a plain equirectangular projection. x grows east, y grows south. ----

private fun viewBox(cam: MapCamera, size: IntSize): GeoBox {
    val halfW = size.width / 2.0 / cam.ppd
    val halfH = size.height / 2.0 / cam.ppd
    return GeoBox(cam.lon - halfW, (cam.lat - halfH).coerceAtLeast(-85.0), cam.lon + halfW, (cam.lat + halfH).coerceAtMost(85.0))
}

private fun toLatLon(p: Offset, cam: MapCamera, size: IntSize) =
    LatLon(cam.lat - (p.y - size.height / 2f) / cam.ppd, cam.lon + (p.x - size.width / 2f) / cam.ppd)

private fun hourIndex(cam: MapCamera, f: WindField): Int =
    if (cam.hour < 0 || cam.hour >= f.times.size) f.nowIndex() else cam.hour

private fun normLonDisplay(lon: Double) = ((lon + 540) % 360) - 180

/** The world copies (in multiples of 360°) that overlap the view, so the map wraps around the date line. */
private fun copies(view: GeoBox): IntRange =
    floor((view.west + 180) / 360).toInt()..floor((view.east + 180) / 360).toInt()

/**
 * The wind particles. Positions are kept in degrees so they stay put while the map pans; each
 * remembers its last few positions to draw a fading trail.
 */
private class Particles(private val density: Float) {
    private val max = 2600
    private val trail = 8
    private val lon = DoubleArray(max)
    private val lat = DoubleArray(max)
    private val age = IntArray(max)
    private val life = IntArray(max)
    private val speed = FloatArray(max)
    private val tLon = DoubleArray(max * trail)
    private val tLat = DoubleArray(max * trail)
    private val len = IntArray(max)
    private var count = 0
    private val rnd = Random(42)
    private val tmp = FloatArray(3)

    // Segments batched by colour band and trail age, so a frame is a few dozen drawLines calls.
    private val bands = WIND_STOPS.size
    private val ageBands = 3
    private val segs = Array(bands * ageBands) { FloatArray(max * 4 * ((trail + ageBands - 1) / ageBands)) }
    private val segN = IntArray(bands * ageBands)
    private val paints = Array(bands * ageBands) { k ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
            color = WIND_COLORS[k / ageBands]
            alpha = intArrayOf(235, 140, 60)[k % ageBands]
        }
    }

    fun clear() {
        for (i in 0 until max) { len[i] = 0; age[i] = life[i] }
    }

    fun step(f: WindField, hour: Int, gusts: Boolean, view: GeoBox, ppd: Float, size: IntSize, dt: Float) {
        count = ((size.width.toLong() * size.height) / (260 * density * density)).toInt().coerceIn(400, max)
        val k = 0.03f * density * dt / ppd // degrees moved per frame per km/h
        for (i in 0 until count) {
            if (age[i] >= life[i]) {
                lon[i] = view.west + rnd.nextDouble() * view.lonSpan
                lat[i] = view.south + rnd.nextDouble() * view.latSpan
                age[i] = 0
                life[i] = 50 + rnd.nextInt(90)
                len[i] = 0
            }
            if (!f.sample(hour, lon[i], lat[i], gusts, tmp)) {
                age[i] = life[i]
                continue
            }
            // Shift the trail back and push the current position.
            val base = i * trail
            System.arraycopy(tLon, base, tLon, base + 1, trail - 1)
            System.arraycopy(tLat, base, tLat, base + 1, trail - 1)
            tLon[base] = lon[i]
            tLat[base] = lat[i]
            if (len[i] < trail) len[i]++
            lon[i] += tmp[0] * k
            lat[i] += tmp[1] * k
            speed[i] = tmp[2]
            age[i]++
            if (lat[i] < view.south || lat[i] > view.north || lon[i] < view.west || lon[i] > view.east) age[i] = life[i]
        }
    }

    fun draw(scope: DrawScope, cam: MapCamera) {
        val w = scope.size.width / 2
        val h = scope.size.height / 2
        val ppd = cam.ppd
        segN.fill(0)
        for (i in 0 until count) {
            val n = len[i]
            if (n == 0) continue
            val band = bandOf(speed[i])
            var px = ((lon[i] - cam.lon) * ppd + w).toFloat()
            var py = ((cam.lat - lat[i]) * ppd + h).toFloat()
            val base = i * trail
            for (j in 0 until n) {
                val qx = ((tLon[base + j] - cam.lon) * ppd + w).toFloat()
                val qy = ((cam.lat - tLat[base + j]) * ppd + h).toFloat()
                val bucket = band * ageBands + (j * ageBands / trail)
                val arr = segs[bucket]
                val o = segN[bucket]
                if (o + 4 <= arr.size) {
                    arr[o] = px; arr[o + 1] = py; arr[o + 2] = qx; arr[o + 3] = qy
                    segN[bucket] = o + 4
                }
                px = qx; py = qy
            }
        }
        val c = scope.drawContext.canvas.nativeCanvas
        for (b in segs.indices) if (segN[b] > 0) c.drawLines(segs[b], 0, segN[b], paints[b])
    }

    private fun bandOf(kmh: Float): Int {
        for (i in WIND_STOPS.indices.reversed()) if (kmh >= WIND_STOPS[i]) return i
        return 0
    }
}

/** Draws everything on the map except the particles. */
private class MapPainter(private val density: Float) {
    private val ocean = Paint().apply { color = 0xFF0B1A2A.toInt() }
    private val landFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2B3B.toInt(); style = Paint.Style.FILL }
    private val coast = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC9FB3C8.toInt(); style = Paint.Style.STROKE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x669FB3C8; style = Paint.Style.STROKE }
    private val heatPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 110 }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * density; color = 0xEEFFFFFF.toInt() }
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x22FFFFFF }
    private val areaEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * density; color = 0x55FFFFFF }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stormStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * density; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 13f * density; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5E7EB.toInt(); textSize = 11f * density }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0B1220.toInt() }
    private val rect = RectF()
    private val path = android.graphics.Path()

    private var heat: Bitmap? = null
    private var heatKey: Triple<WindField, Int, Boolean>? = null

    fun drawBase(s: DrawScope, cam: MapCamera, world: WorldOutline?) {
        val c = s.drawContext.canvas.nativeCanvas
        c.drawRect(0f, 0f, s.size.width, s.size.height, ocean)
        world ?: return
        forEachCopy(s, cam) { c.drawPath(world.land, landFill) }
    }

    fun drawCoasts(s: DrawScope, cam: MapCamera, world: WorldOutline?) {
        world ?: return
        val c = s.drawContext.canvas.nativeCanvas
        coast.strokeWidth = 1f * density / cam.ppd
        border.strokeWidth = 0.8f * density / cam.ppd
        forEachCopy(s, cam) {
            c.drawPath(world.land, coast)
            c.drawPath(world.borders, border)
        }
    }

    /** Applies the degrees → pixels transform once for each world copy in view. */
    private inline fun forEachCopy(s: DrawScope, cam: MapCamera, block: () -> Unit) {
        val c = s.drawContext.canvas.nativeCanvas
        val view = viewOf(s, cam)
        for (k in copies(view)) {
            c.save()
            c.translate((s.size.width / 2 + (k * 360 - cam.lon) * cam.ppd).toFloat(), (s.size.height / 2 + cam.lat * cam.ppd).toFloat())
            c.scale(cam.ppd, cam.ppd)
            block()
            c.restore()
        }
    }

    private fun viewOf(s: DrawScope, cam: MapCamera): GeoBox {
        val hw = s.size.width / 2.0 / cam.ppd
        val hh = s.size.height / 2.0 / cam.ppd
        return GeoBox(cam.lon - hw, cam.lat - hh, cam.lon + hw, cam.lat + hh)
    }

    private fun x(cam: MapCamera, s: DrawScope, lon: Double) = ((lon - cam.lon) * cam.ppd + s.size.width / 2).toFloat()
    private fun y(cam: MapCamera, s: DrawScope, lat: Double) = ((cam.lat - lat) * cam.ppd + s.size.height / 2).toFloat()

    /** A soft colour wash of wind speed under the particles, so storms stand out at a glance. */
    fun drawHeat(s: DrawScope, cam: MapCamera, f: WindField, hour: Int, gusts: Boolean) {
        val key = Triple(f, hour, gusts)
        if (heatKey != key) {
            val values = if (gusts) f.gust[hour] else f.speed[hour]
            val px = IntArray(f.nx * f.ny) { k ->
                val i = k % f.nx
                val row = f.ny - 1 - k / f.nx // bitmap row 0 is the north edge
                val v = values[row * f.nx + i]
                if (v.isNaN()) 0 else windColor(v)
            }
            heat = Bitmap.createBitmap(px, f.nx, f.ny, Bitmap.Config.ARGB_8888)
            heatKey = key
        }
        val bmp = heat ?: return
        val c = s.drawContext.canvas.nativeCanvas
        // Grid points sit at pixel centres, so the picture reaches half a cell past the outer points.
        val hx = f.box.lonSpan / (f.nx - 1) / 2
        val hy = f.box.latSpan / (f.ny - 1) / 2
        val view = viewOf(s, cam)
        val shifts = if (f.box.lonSpan >= 359.9) copies(view).map { it * 360.0 } else listOf(nearestShift(f.box, view))
        for (shift in shifts) {
            rect.set(
                x(cam, s, f.box.west - hx + shift), y(cam, s, f.box.north + hy),
                x(cam, s, f.box.east + hx + shift), y(cam, s, f.box.south - hy),
            )
            c.drawBitmap(bmp, null, rect, heatPaint)
        }
    }

    /** The multiple of 360° that brings [box] closest to [view]. */
    private fun nearestShift(box: GeoBox, view: GeoBox): Double {
        val boxMid = (box.west + box.east) / 2
        val viewMid = (view.west + view.east) / 2
        return Math.round((viewMid - boxMid) / 360.0) * 360.0
    }

    fun drawStorms(s: DrawScope, cam: MapCamera, storms: List<Storm>, selected: String?) {
        val c = s.drawContext.canvas.nativeCanvas
        val view = viewOf(s, cam)
        for (storm in storms) {
            val track = storm.track
            for (k in copies(view)) {
                val shift = k * 360.0
                if (track != null) {
                    for (area in track.areas) {
                        polyPath(cam, s, area, shift, close = true)
                        c.drawPath(path, areaPaint)
                        c.drawPath(path, areaEdge)
                    }
                    trackPaint.color = stormColor(storm.category)
                    for (line in track.lines) {
                        polyPath(cam, s, line, shift, close = false)
                        c.drawPath(path, trackPaint)
                    }
                    for (p in track.points) {
                        val lon = unwrapNear(p.lon, storm.lon) + shift
                        dot.color = if (p.forecast) 0xAAFFFFFF.toInt() else stormColor(storm.category)
                        c.drawCircle(x(cam, s, lon), y(cam, s, p.lat), 2.5f * density, dot)
                    }
                }
                drawStormMarker(c, x(cam, s, storm.lon + shift), y(cam, s, storm.lat), storm, storm.key == selected)
            }
        }
    }

    /** Builds [path] from a lat/lon line, keeping it continuous across the date line. */
    private fun polyPath(cam: MapCamera, s: DrawScope, pts: List<LatLon>, shift: Double, close: Boolean) {
        path.reset()
        var prev = Double.NaN
        pts.forEachIndexed { i, p ->
            val lon = if (prev.isNaN()) p.lon else unwrapNear(p.lon, prev)
            prev = lon
            val px = x(cam, s, lon + shift)
            val py = y(cam, s, p.lat)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (close) path.close()
    }

    private fun unwrapNear(lon: Double, ref: Double): Double {
        var l = lon
        while (l - ref > 180) l -= 360
        while (ref - l > 180) l += 360
        return l
    }

    /** A cyclone symbol: a ring with two curling arms, spun the right way for the hemisphere. */
    private fun drawStormMarker(c: android.graphics.Canvas, cx: Float, cy: Float, storm: Storm, selected: Boolean) {
        val r = (if (selected) 14f else 11f) * density
        val color = stormColor(storm.category)
        stormStroke.color = color
        dot.color = color
        c.drawCircle(cx, cy, r * 0.45f, dot)
        c.drawCircle(cx, cy, r * 0.8f, stormStroke)
        val spin = if (storm.lat >= 0) -1f else 1f // anticlockwise in the north
        for (arm in 0..1) {
            path.reset()
            val a0 = arm * Math.PI
            path.moveTo(cx + (r * 0.8f * cos(a0)).toFloat(), cy + (r * 0.8f * sin(a0)).toFloat())
            for (step in 1..6) {
                val a = a0 + spin * step * 0.35
                val rr = r * (0.8f + step * 0.18f)
                path.lineTo(cx + (rr * cos(a)).toFloat(), cy + (rr * sin(a)).toFloat())
            }
            c.drawPath(path, stormStroke)
        }
        if (selected) {
            stormStroke.alpha = 120
            c.drawCircle(cx, cy, r * 2.2f, stormStroke)
            stormStroke.alpha = 255
        }
        // Name label, with the strength underneath.
        val name = storm.name.uppercase()
        val sub = storm.kind.substringAfter("· ", storm.kind)
        val tw = maxOf(label.measureText(name), small.measureText(sub))
        val lx = cx + r * 2f
        val ly = cy - r * 0.6f
        rect.set(lx - 6 * density, ly - label.textSize, lx + tw + 6 * density, ly + small.textSize + 6 * density)
        c.drawRoundRect(rect, 6 * density, 6 * density, labelBg)
        c.drawText(name, lx, ly, label)
        c.drawText(sub, lx, ly + small.textSize + 2 * density, small)
    }

    fun drawPlace(s: DrawScope, cam: MapCamera, at: LatLon, name: String) {
        val c = s.drawContext.canvas.nativeCanvas
        for (k in copies(viewOf(s, cam))) {
            val px = x(cam, s, at.lon + k * 360)
            val py = y(cam, s, at.lat)
            dot.color = 0xFFFFFFFF.toInt()
            c.drawCircle(px, py, 5f * density, dot)
            dot.color = 0xFF0EA5E9.toInt()
            c.drawCircle(px, py, 3.5f * density, dot)
            c.drawText(name, px + 8 * density, py + 4 * density, small)
        }
    }

    fun drawProbe(s: DrawScope, cam: MapCamera, at: LatLon) {
        val c = s.drawContext.canvas.nativeCanvas
        stormStroke.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x(cam, s, at.lon), y(cam, s, at.lat), 7f * density, stormStroke)
    }

    /** The storm whose marker is under [p], if any. */
    fun stormAt(p: Offset, storms: List<Storm>, cam: MapCamera, size: IntSize): Storm? {
        if (cam.ppd <= 0f) return null
        val w = size.width / 2f
        val h = size.height / 2f
        val view = viewBox(cam, size)
        return storms.flatMap { st ->
            copies(view).map { k ->
                val sx = ((st.lon + k * 360 - cam.lon) * cam.ppd + w).toFloat()
                val sy = ((cam.lat - st.lat) * cam.ppd + h).toFloat()
                st to hypot(sx - p.x, sy - p.y)
            }
        }.filter { it.second < 36 * density }.minByOrNull { it.second }?.first
    }
}
