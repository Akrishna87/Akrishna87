package io.github.akrishna87.daybook.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.daybook.DeviceCalendar
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.lock.LockPhoto
import io.github.akrishna87.daybook.lock.LockRenderer
import io.github.akrishna87.daybook.lock.LockSnapshot
import io.github.akrishna87.daybook.lock.LockWallpaperService
import io.github.akrishna87.daybook.model.Backgrounds
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun isWallpaperActive(context: Context): Boolean =
    runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo?.component?.className == LockWallpaperService::class.java.name
    }.getOrDefault(false)

/** Opens Android's live wallpaper preview for Daybook, where "Set wallpaper" puts it on the lock screen. */
fun openWallpaperPicker(context: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, LockWallpaperService::class.java))
    try {
        context.startActivity(direct)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            Toast.makeText(context, "Choose \"Daybook lock screen\"", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "This phone doesn't support live wallpapers", Toast.LENGTH_LONG).show()
        }
    }
}

/** Sets up the lock screen: a live preview, the button to apply it, its background and what it shows. */
@Composable
fun LockSettingsScreen(
    data: Data,
    events: List<Event>,
    now: LocalDateTime,
    is24Hour: Boolean,
    photo: Bitmap?,
    wallpaperActive: Boolean,
    onPhotoChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = data.settings
    BackHandler(onBack = onClose)

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { LockPhoto.save(context, uri) }
                if (ok) {
                    Store.updateSettings { it.copy(photo = true) }
                    onPhotoChanged()
                } else {
                    Toast.makeText(context, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Store.updateSettings { it.copy(deviceCalendars = true) }
        } else {
            Toast.makeText(context, "Daybook needs calendar access to show your phone's calendars", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(settings.background).deep(0.2f))
            .statusBarsPadding(),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Lock screen", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            LockPreview(
                LockSnapshot(now, events, data.tasks, settings, is24Hour),
                photo,
                is24Hour,
                Modifier.align(Alignment.CenterHorizontally).width(230.dp).padding(vertical = 8.dp),
            )
            if (wallpaperActive) {
                Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                    Text("Daybook is on your lock screen", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
            val setLabel = if (wallpaperActive) "Set again" else "Set as lock screen"
            Button(
                onClick = { openWallpaperPicker(context) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { contentDescription = setLabel },
            ) { Text(setLabel) }
            Text(
                "Android draws its own clock and notifications at the top; Daybook fills the rest. " +
                    "On the next screen tap \"Set wallpaper\", and choose \"Home and lock screens\" if asked. " +
                    "When the phone is unlocked your home screen shows just the background.\n\n" +
                    "Tip: if a big clock covers the middle of your lock screen, turn off " +
                    "\"Double-line clock\" in your phone's Settings → Display → Lock screen.",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SubText,
            )

            Heading("Background")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((name, c) in Backgrounds.choices) {
                    val chosen = c == settings.background && !settings.photo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (chosen) 2.5.dp else 1.dp, if (chosen) Color.White else Palette.GlassBorder, CircleShape)
                                .clickable(role = Role.RadioButton, onClickLabel = name) {
                                    Store.updateSettings { it.copy(background = c, photo = false) }
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (chosen) Icon(Icons.Rounded.Check, null) }
                        Text(name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Palette.SubText)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                    Text(if (settings.photo) "Change photo" else "Use a photo", Modifier.padding(start = 6.dp))
                }
                if (settings.photo) {
                    OutlinedButton(onClick = {
                        Store.updateSettings { it.copy(photo = false) }
                        LockPhoto.delete(context)
                        onPhotoChanged()
                    }) { Text("Remove photo") }
                }
            }
            if (settings.photo) {
                SliderRow("Darken the photo", settings.dim, 0f..0.7f) { v -> Store.updateSettings { it.copy(dim = v) } }
            }

            Heading("Layout")
            SliderRow("Start below the clock", settings.topOffset, 0.12f..0.55f) { v -> Store.updateSettings { it.copy(topOffset = v) } }
            Text(
                "Move this until the calendar sits just under your phone's clock.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SubText,
            )
            SwitchRow("Month calendar", settings.showMonth) { v -> Store.updateSettings { it.copy(showMonth = v) } }
            SwitchRow("Today's plan", settings.showTimeline) { v -> Store.updateSettings { it.copy(showTimeline = v) } }
            SwitchRow("Year progress", settings.showYear) { v -> Store.updateSettings { it.copy(showYear = v) } }
            SwitchRow("Today, tomorrow and tasks card", settings.showCard) { v -> Store.updateSettings { it.copy(showCard = v) } }
            SwitchRow("Show on the home screen too", settings.onHomeScreen) { v -> Store.updateSettings { it.copy(onHomeScreen = v) } }

            Heading("Calendar")
            SwitchRow("Week starts on Sunday", settings.weekStartsSunday) { v -> Store.updateSettings { it.copy(weekStartsSunday = v) } }
            SwitchRow(
                "Show my phone's calendars",
                settings.deviceCalendars && DeviceCalendar.hasPermission(context),
                "Google Calendar, holidays and others, next to Daybook's own events. Read only.",
            ) { on ->
                when {
                    !on -> Store.updateSettings { it.copy(deviceCalendars = false) }
                    DeviceCalendar.hasPermission(context) -> Store.updateSettings { it.copy(deviceCalendars = true) }
                    else -> askCalendar.launch(Manifest.permission.READ_CALENDAR)
                }
            }
        }
    }
}

/** The lock screen as it will look, drawn by the same code as the wallpaper, with a stand-in clock. */
@Composable
private fun LockPreview(snapshot: LockSnapshot, photo: Bitmap?, is24Hour: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(modifier.aspectRatio(9f / 19.5f).clip(shape).border(2.dp, Color.White.copy(alpha = 0.5f), shape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { LockRenderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), snapshot, photo) }
        }
        val w = maxWidth.value
        Column(Modifier.fillMaxWidth().padding(top = (w * 0.12f).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DateTimeFormatter.ofPattern("EEE d MMM").format(snapshot.now),
                fontSize = (w * 0.045f).sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Text(
                DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm").format(snapshot.now),
                fontSize = (w * 0.24f).sp,
                lineHeight = (w * 0.26f).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Column(Modifier.padding(top = 24.dp)) {
        HorizontalDivider(color = Palette.GlassBorder)
        Text(text, Modifier.padding(top = 16.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    // Only save when the finger lifts, not on every step of the drag.
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = dragging.coerceIn(range),
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Palette.GlassBorder),
        )
    }
}
