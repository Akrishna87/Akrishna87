package io.github.akrishna87.daybook

import io.github.akrishna87.daybook.model.Backgrounds
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Note
import io.github.akrishna87.daybook.model.Settings
import io.github.akrishna87.daybook.model.Task
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val data = Data(
            events = listOf(
                Event(title = "Gym", date = LocalDate.of(2026, 10, 2), start = LocalTime.of(18, 0), end = LocalTime.of(20, 0)),
                Event(title = "Holiday", date = LocalDate.of(2026, 10, 3)),
            ),
            tasks = listOf(Task(title = "Groceries", due = LocalDate.of(2026, 10, 4)), Task(title = "Gym", done = true, doneAt = 5)),
            notes = listOf(Note(title = "Ideas", body = "Line one\nLine two", pinned = true, color = 0xFFC69CF4.toInt())),
            settings = Settings(background = Backgrounds.Ocean, photo = true, dim = 0.4f, topOffset = 0.33f, weekStartsSunday = true, showYear = false),
        )
        val loaded = Data.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(data, loaded)
        assertTrue(loaded.events[1].allDay)
    }

    @Test
    fun missingSettingsFallBackToDefaults() {
        val loaded = Data.fromJson(JSONObject("""{"events":[],"tasks":[{"title":"Old task"}]}"""))
        assertEquals(Settings(), loaded.settings)
        assertEquals("Old task", loaded.tasks.single().title)
        assertTrue(loaded.tasks.single().id.isNotEmpty())
    }

    @Test
    fun firstRunHasTheWelcomeNote() {
        assertEquals("Welcome to Daybook", Data.firstRun().notes.single().title)
    }
}
