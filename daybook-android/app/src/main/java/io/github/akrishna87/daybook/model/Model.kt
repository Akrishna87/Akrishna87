package io.github.akrishna87.daybook.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Colours for events, tasks and notes: soft pastels that read well on the lock screen. */
object Colors {
    val Lilac = 0xFFC69CF4.toInt()
    val Sky = 0xFF8EC5FF.toInt()
    val Mint = 0xFF7FE0B8.toInt()
    val Peach = 0xFFFFB38A.toInt()
    val Rose = 0xFFFF8FA8.toInt()
    val Lemon = 0xFFFFE28A.toInt()
    val Silver = 0xFFD5D9E6.toInt()
    val Orchid = 0xFFC45BF0.toInt()

    val choices = listOf(Lilac, Sky, Mint, Peach, Rose, Lemon, Silver, Orchid)
}

/**
 * Something on the calendar. Events made in Daybook last part of one day (or all of it);
 * events read from the phone's calendars can run over several days.
 */
data class Event(
    val id: String = newId(),
    val title: String,
    val date: LocalDate,
    /** The last day it covers, inclusive. */
    val endDate: LocalDate = date,
    /** Null for an all-day event. */
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val color: Int = Colors.Lilac,
    /** Read from one of the phone's calendars, so Daybook can show it but not change it. */
    val fromDevice: Boolean = false,
) {
    val allDay: Boolean get() = start == null

    fun occursOn(day: LocalDate): Boolean = !day.isBefore(date) && !day.isAfter(endDate)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date.toString())
        .put("endDate", endDate.toString())
        .put("start", start?.toString() ?: "")
        .put("end", end?.toString() ?: "")
        .put("color", color)

    companion object {
        fun fromJson(o: JSONObject): Event {
            val date = LocalDate.parse(o.getString("date"))
            return Event(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                date = date,
                endDate = o.optString("endDate").takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: date,
                start = o.optString("start").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                end = o.optString("end").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                color = o.optInt("color", Colors.Lilac),
            )
        }
    }
}

data class Task(
    val id: String = newId(),
    val title: String,
    val done: Boolean = false,
    val due: LocalDate? = null,
    val color: Int = Colors.Orchid,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("done", done)
        .put("due", due?.toString() ?: "")
        .put("color", color)
        .put("createdAt", createdAt)
        .put("doneAt", doneAt)

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.optString("id").ifEmpty { newId() },
            title = o.optString("title"),
            done = o.optBoolean("done"),
            due = o.optString("due").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            color = o.optInt("color", Colors.Orchid),
            createdAt = o.optLong("createdAt"),
            doneAt = o.optLong("doneAt"),
        )
    }
}

data class Note(
    val id: String = newId(),
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    /** 0 for the plain glass card. */
    val color: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("pinned", pinned)
        .put("color", color)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = Note(
            id = o.optString("id").ifEmpty { newId() },
            title = o.optString("title"),
            body = o.optString("body"),
            pinned = o.optBoolean("pinned"),
            color = o.optInt("color"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}

/** The lock screen's look and what it shows, plus app-wide preferences. */
data class Settings(
    /** The background colour, used under the photo too. */
    val background: Int = Backgrounds.Latte,
    /** A photo from the gallery is saved as the lock screen background. */
    val photo: Boolean = false,
    /** How much to darken the photo so the text stays readable, 0 to 1. */
    val dim: Float = 0.25f,
    /** Where the lock screen layout starts, as a share of the screen height, below Android's clock. */
    val topOffset: Float = 0.30f,
    val weekStartsSunday: Boolean = false,
    /** Show events from the phone's own calendars (Google Calendar and so on) next to Daybook's. */
    val deviceCalendars: Boolean = false,
    val showMonth: Boolean = true,
    val showTimeline: Boolean = true,
    val showYear: Boolean = true,
    val showCard: Boolean = true,
    /** Draw the layout on the home screen too, not only while the phone is locked. */
    val onHomeScreen: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("background", background)
        .put("photo", photo)
        .put("dim", dim.toDouble())
        .put("topOffset", topOffset.toDouble())
        .put("weekStartsSunday", weekStartsSunday)
        .put("deviceCalendars", deviceCalendars)
        .put("showMonth", showMonth)
        .put("showTimeline", showTimeline)
        .put("showYear", showYear)
        .put("showCard", showCard)
        .put("onHomeScreen", onHomeScreen)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                background = o.optInt("background", d.background),
                photo = o.optBoolean("photo", d.photo),
                dim = o.optDouble("dim", d.dim.toDouble()).toFloat(),
                topOffset = o.optDouble("topOffset", d.topOffset.toDouble()).toFloat(),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                deviceCalendars = o.optBoolean("deviceCalendars", d.deviceCalendars),
                showMonth = o.optBoolean("showMonth", d.showMonth),
                showTimeline = o.optBoolean("showTimeline", d.showTimeline),
                showYear = o.optBoolean("showYear", d.showYear),
                showCard = o.optBoolean("showCard", d.showCard),
                onHomeScreen = o.optBoolean("onHomeScreen", d.onHomeScreen),
            )
        }
    }
}

/** Background colours to pick from. White text stays readable on every one. */
object Backgrounds {
    val Latte = 0xFF8F6E5C.toInt()
    val Plum = 0xFF4B3566.toInt()
    val Ocean = 0xFF2F4E6B.toInt()
    val Forest = 0xFF3E5A47.toInt()
    val Rosewood = 0xFF8A4F5E.toInt()
    val Night = 0xFF1C1C22.toInt()

    val choices = listOf("Latte" to Latte, "Plum" to Plum, "Ocean" to Ocean, "Forest" to Forest, "Rosewood" to Rosewood, "Night" to Night)
}

/** Everything Daybook keeps, saved as one JSON file. */
data class Data(
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("events", JSONArray(events.map { it.toJson() }))
        .put("tasks", JSONArray(tasks.map { it.toJson() }))
        .put("notes", JSONArray(notes.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject) = Data(
            events = o.optJSONArray("events").objects().map(Event::fromJson),
            tasks = o.optJSONArray("tasks").objects().map(Task::fromJson),
            notes = o.optJSONArray("notes").objects().map(Note::fromJson),
            settings = Settings.fromJson(o.optJSONObject("settings")),
        )

        /** What a new install starts with: a note on how to put Daybook on the lock screen. */
        fun firstRun() = Data(
            notes = listOf(
                Note(
                    title = "Welcome to Daybook",
                    body = "Your calendar, tasks and notes in one place.\n\n" +
                        "To see them on your lock screen, open Today, tap the lock screen button at the top " +
                        "and choose \"Set as lock screen\". Daybook draws your month, today's plan, the year " +
                        "so far and your tasks under Android's clock.\n\n" +
                        "Add the Daybook widget to your home screen for the same Today, Tomorrow and Tasks card.",
                    pinned = true,
                ),
            ),
        )
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }
