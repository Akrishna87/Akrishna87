package io.github.akrishna87.daybook

import android.content.Context
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Note
import io.github.akrishna87.daybook.model.Settings
import io.github.akrishna87.daybook.model.Task
import io.github.akrishna87.daybook.widget.AgendaWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Daybook's events, tasks, notes and settings, kept in memory and saved to one JSON file.
 * The app, the lock screen wallpaper and the widget all run in the same process and read the
 * same [data], so a change anywhere shows up everywhere at once.
 */
object Store {
    private val state = MutableStateFlow(Data())
    val data: StateFlow<Data> = state

    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private lateinit var app: Context

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        app = context.applicationContext
        val f = File(app.filesDir, "daybook.json")
        file = f
        val loaded = runCatching { if (f.exists()) Data.fromJson(JSONObject(f.readText())) else null }.getOrNull()
        state.value = loaded ?: Data.firstRun()
        if (loaded == null) write(state.value)
    }

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
        AgendaWidget.refresh(app)
    }

    private fun write(d: Data) {
        val f = file ?: return
        val json = d.toJson().toString()
        writer.execute {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }

    fun saveEvent(e: Event) = update { d -> d.copy(events = d.events.filter { it.id != e.id } + e) }
    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filter { it.id != id }) }

    fun saveTask(t: Task) = update { d ->
        val i = d.tasks.indexOfFirst { it.id == t.id }
        d.copy(tasks = if (i < 0) d.tasks + t else d.tasks.toMutableList().also { it[i] = t })
    }

    fun toggleTask(id: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = !it.done, doneAt = if (it.done) 0L else System.currentTimeMillis()) else it })
    }

    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.filter { it.id != id }) }
    fun clearDoneTasks() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    /** Saves a note, or drops it when it was left empty. */
    fun saveNote(n: Note) = update { d ->
        val others = d.notes.filter { it.id != n.id }
        d.copy(notes = if (n.isBlank) others else others + n)
    }

    fun deleteNote(id: String) = update { d -> d.copy(notes = d.notes.filter { it.id != id }) }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
