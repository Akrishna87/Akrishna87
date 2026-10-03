package io.github.akrishna87.daybook.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.daybook.DeviceCalendar
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.lock.LockPhoto
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Note
import io.github.akrishna87.daybook.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.WbSunny),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    TASKS("Tasks", Icons.Rounded.CheckBox),
    NOTES("Notes", Icons.Rounded.EditNote),
}

@Composable
fun DaybookApp() {
    val context = LocalContext.current
    val data by Store.data.collectAsStateWithLifecycle()
    val settings = data.settings
    val now = rememberNow()
    val today = now.toLocalDate()
    val is24Hour = DateFormat.is24HourFormat(context)

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf(today) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    val deviceEvents = rememberDeviceEvents(settings.deviceCalendars, month, today, resumes)
    val events = remember(data.events, deviceEvents) { data.events + deviceEvents }
    val wallpaperActive = remember(resumes) { isWallpaperActive(context) }
    var photoVersion by remember { mutableIntStateOf(0) }
    val photo = rememberLockPhoto(settings.photo, photoVersion)

    var editingEvent by remember { mutableStateOf<Pair<Event, Boolean>?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var openNote by remember { mutableStateOf<Note?>(null) }
    var showLockSettings by remember { mutableStateOf(false) }

    fun editEvent(e: Event) {
        if (e.fromDevice) {
            Toast.makeText(context, "This is from your phone's calendar. Change it in your calendar app.", Toast.LENGTH_LONG).show()
        } else {
            editingEvent = e to false
        }
    }

    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }

    val background = Color(settings.background)
    DaybookTheme(background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(lerp(background, Color.White, 0.10f), lerp(background, Color.Black, 0.22f)))),
        ) {
            if (photo != null) {
                Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (settings.dim + 0.12f).coerceAtMost(0.85f))))
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                bottomBar = { BottomBar(tab) { tab = it } },
                floatingActionButton = {
                    when (tab) {
                        Tab.CALENDAR -> Fab("New event") { editingEvent = newEventOn(selectedDay) to true }
                        Tab.NOTES -> Fab("New note") { openNote = Note() }
                        else -> {}
                    }
                },
            ) { padding ->
                Crossfade(targetState = tab, label = "tab") { t ->
                    when (t) {
                        Tab.TODAY -> TodayScreen(
                            data = data,
                            events = events,
                            now = now,
                            is24Hour = is24Hour,
                            wallpaperActive = wallpaperActive,
                            padding = padding,
                            onOpenSettings = { showLockSettings = true },
                            onOpenDay = { day ->
                                selectedDay = day
                                month = YearMonth.from(day)
                                tab = Tab.CALENDAR
                            },
                            onEditEvent = ::editEvent,
                            onAddEvent = { editingEvent = newEventOn(today) to true },
                            onOpenTasks = { tab = Tab.TASKS },
                        )
                        Tab.CALENDAR -> CalendarScreen(
                            month = month,
                            selected = selectedDay,
                            now = now,
                            events = events,
                            settings = settings,
                            is24Hour = is24Hour,
                            padding = padding,
                            onMonthChange = { month = it },
                            onSelect = { selectedDay = it },
                            onEditEvent = ::editEvent,
                        )
                        Tab.TASKS -> TasksScreen(data.tasks, today, padding) { editingTask = it }
                        Tab.NOTES -> NotesScreen(data.notes, padding) { openNote = it }
                    }
                }
            }

            editingEvent?.let { (e, isNew) ->
                key(e.id) { EventEditor(e, isNew, is24Hour) { editingEvent = null } }
            }
            editingTask?.let { t ->
                key(t.id) { TaskEditor(t, today) { editingTask = null } }
            }
            openNote?.let { n ->
                key(n.id) { NoteEditor(n, background) { openNote = null } }
            }
            if (showLockSettings) {
                LockSettingsScreen(
                    data = data,
                    events = events,
                    now = now,
                    is24Hour = is24Hour,
                    photo = photo?.asAndroidBitmap(),
                    wallpaperActive = wallpaperActive,
                    onPhotoChanged = { photoVersion++ },
                    onClose = { showLockSettings = false },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Color.Black.copy(alpha = 0.22f), contentColor = Color.White, tonalElevation = 0.dp) {
        for (t in Tab.entries) {
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.TodayFill,
                    selectedTextColor = Color.White,
                    indicatorColor = Color.White,
                    unselectedIconColor = Palette.SubText,
                    unselectedTextColor = Palette.SubText,
                ),
            )
        }
    }
}

@Composable
private fun Fab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        // Label the button itself, so the label reaches accessibility services and UI tests.
        modifier = Modifier.semantics { contentDescription = label },
        icon = { Icon(Icons.Rounded.Add, null) },
        text = { Text(label) },
        containerColor = Color.White,
        contentColor = Palette.TodayFill,
    )
}

/** The current time, updated on the minute. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val n = LocalDateTime.now()
            value = n
            delay(60_000L - n.second * 1000L - n.nano / 1_000_000L)
        }
    }
    return now
}

/** Events from the phone's calendars around the month on show and the next few days, when turned on. */
@Composable
private fun rememberDeviceEvents(enabled: Boolean, month: YearMonth, today: LocalDate, refresh: Int): List<Event> {
    val context = LocalContext.current
    val events by produceState(emptyList<Event>(), enabled, month, today, refresh) {
        value = if (!enabled || !DeviceCalendar.hasPermission(context)) {
            emptyList()
        } else {
            val from = minOf(month.atDay(1).minusDays(7), today)
            val until = maxOf(month.atEndOfMonth().plusDays(8), today.plusDays(3))
            withContext(Dispatchers.IO) { DeviceCalendar.events(context, from, until) }
        }
    }
    return events
}

@Composable
private fun rememberLockPhoto(enabled: Boolean, version: Int): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, enabled, version) {
        value = if (!enabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val m = context.resources.displayMetrics
                LockPhoto.load(context, m.widthPixels, m.heightPixels)?.asImageBitmap()
            }
        }
    }
    return photo
}
