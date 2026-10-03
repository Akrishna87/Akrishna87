package io.github.akrishna87.daybook

import android.content.Context
import io.github.akrishna87.daybook.model.DEFAULT_NOTEBOOK_ID
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.INBOX_ID
import io.github.akrishna87.daybook.model.Note
import io.github.akrishna87.daybook.model.Notebook
import io.github.akrishna87.daybook.model.Project
import io.github.akrishna87.daybook.model.Repeat
import io.github.akrishna87.daybook.model.Settings
import io.github.akrishna87.daybook.model.Task
import io.github.akrishna87.daybook.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Daybook's tasks, projects, notes, notebooks and settings, kept in memory and saved to one JSON
 * file. The app, the widget and the reminders all read the same [data], so a change anywhere shows
 * up everywhere at once.
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
        if (loaded == null || !f.readTextOrEmpty().contains("\"version\":2")) write(state.value)
    }

    private fun File.readTextOrEmpty() = runCatching { readText() }.getOrDefault("")

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
        TodayWidget.refresh(app)
        Reminders.sync(app, next)
    }

    /** Replaces everything, for restoring a backup. */
    fun replaceAll(d: Data) = update { d }

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

    // Tasks

    fun saveTask(t: Task) = update { d ->
        val i = d.tasks.indexOfFirst { it.id == t.id }
        d.copy(tasks = if (i < 0) d.tasks + t else d.tasks.toMutableList().also { it[i] = t })
    }

    /** Puts tasks back exactly as they were (for Undo), adding any that were deleted. */
    fun restoreTasks(tasks: List<Task>) = update { d ->
        val byId = tasks.associateBy { it.id }
        val kept = d.tasks.map { byId[it.id] ?: it }
        d.copy(tasks = kept + tasks.filter { t -> d.tasks.none { it.id == t.id } })
    }

    /** The task and its subtasks, as they are now, for Undo. */
    fun taskFamily(id: String): List<Task> = state.value.tasks.filter { it.id == id || it.parentId == id }

    /**
     * Completes a task and its subtasks. A repeating task with a date moves to its next date
     * instead, and that date is returned.
     */
    fun completeTask(id: String, today: LocalDate = LocalDate.now()): LocalDate? {
        var nextDate: LocalDate? = null
        update { d ->
            val t = d.task(id) ?: return@update d
            val now = System.currentTimeMillis()
            val due = t.due
            if (t.repeat != Repeat.NONE && due != null) {
                val next = Dates.nextDue(due, t.repeat, today)
                nextDate = next
                d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(due = next) else it })
            } else {
                d.copy(tasks = d.tasks.map { if (it.id == id || (it.parentId == id && !it.done)) it.copy(done = true, doneAt = now) else it })
            }
        }
        return nextDate
    }

    fun uncompleteTask(id: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = false, doneAt = 0L) else it })
    }

    /** Deletes a task with its subtasks. */
    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.filter { it.id != id && it.parentId != id }) }

    /** Moves every overdue task to today. */
    fun rescheduleOverdue(today: LocalDate = LocalDate.now()) = update { d ->
        d.copy(tasks = d.tasks.map { if (!it.done && it.due != null && it.due.isBefore(today)) it.copy(due = today) else it })
    }

    fun clearCompleted() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    fun renameLabel(old: String, new: String) = update { d ->
        d.copy(tasks = d.tasks.map { t -> t.copy(labels = t.labels.map { if (it.equals(old, true)) new else it }.distinctBy { it.lowercase() }) })
    }

    fun deleteLabel(label: String) = update { d ->
        d.copy(tasks = d.tasks.map { t -> t.copy(labels = t.labels.filterNot { it.equals(label, true) }) })
    }

    // Projects

    fun saveProject(p: Project) = update { d ->
        val i = d.projects.indexOfFirst { it.id == p.id }
        d.copy(projects = if (i < 0) d.projects + p else d.projects.toMutableList().also { it[i] = p })
    }

    /** Deletes a project and its tasks. */
    fun deleteProject(id: String) = update { d ->
        if (id == INBOX_ID) d else d.copy(projects = d.projects.filter { it.id != id }, tasks = d.tasks.filter { it.projectId != id })
    }

    // Notes

    /** Saves a note, or drops it when it was left empty. */
    fun saveNote(n: Note) = update { d ->
        val i = d.notes.indexOfFirst { it.id == n.id }
        val notes = when {
            n.isBlank -> d.notes.filter { it.id != n.id }
            i < 0 -> d.notes + n
            else -> d.notes.toMutableList().also { it[i] = n }
        }
        d.copy(notes = notes)
    }

    fun trashNote(id: String) = update { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(trashedAt = System.currentTimeMillis(), pinned = false) else it })
    }

    fun restoreNote(id: String) = update { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(trashedAt = 0L) else it })
    }

    /** Deletes a note for good; its tasks stay but lose the link. */
    fun deleteNoteForever(id: String) = update { d ->
        d.copy(notes = d.notes.filter { it.id != id }, tasks = d.tasks.map { if (it.noteId == id) it.copy(noteId = null) else it })
    }

    fun emptyTrash() = update { d ->
        val gone = d.notes.filter { it.trashed }.map { it.id }.toSet()
        d.copy(notes = d.notes.filter { !it.trashed }, tasks = d.tasks.map { if (it.noteId != null && it.noteId in gone) it.copy(noteId = null) else it })
    }

    fun renameTag(old: String, new: String) = update { d ->
        d.copy(notes = d.notes.map { n -> n.copy(tags = n.tags.map { if (it.equals(old, true)) new else it }.distinctBy { it.lowercase() }) })
    }

    // Notebooks

    fun saveNotebook(nb: Notebook) = update { d ->
        val i = d.notebooks.indexOfFirst { it.id == nb.id }
        d.copy(notebooks = if (i < 0) d.notebooks + nb else d.notebooks.toMutableList().also { it[i] = nb })
    }

    /** Deletes a notebook; its notes move to the first remaining notebook. There's always at least one. */
    fun deleteNotebook(id: String) = update { d ->
        val rest = d.notebooks.filter { it.id != id }
        if (rest.isEmpty()) return@update d
        val target = rest.firstOrNull { it.id == DEFAULT_NOTEBOOK_ID } ?: rest.first()
        d.copy(notebooks = rest, notes = d.notes.map { if (it.notebookId == id) it.copy(notebookId = target.id) else it })
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
