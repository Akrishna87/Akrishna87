package io.github.akrishna87.daybook.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val INBOX_ID = "inbox"
const val DEFAULT_NOTEBOOK_ID = "notebook-default"

/** Priority 1 is the most urgent; 4 means no priority, as in Todoist. */
object Priority {
    const val P1 = 1
    const val P2 = 2
    const val P3 = 3
    const val NONE = 4

    fun color(p: Int): Int = when (p) {
        P1 -> 0xFFD1453B.toInt()
        P2 -> 0xFFEB8909.toInt()
        P3 -> 0xFF246FE0.toInt()
        else -> 0xFF8A8C99.toInt()
    }

    fun label(p: Int): String = if (p in P1..P3) "Priority $p" else "No priority"
}

enum class Repeat(val label: String) {
    NONE("Doesn't repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/** Colours for projects. */
object ProjectColors {
    val choices = listOf(
        0xFFD1453B.toInt(), // red
        0xFFEB8909.toInt(), // orange
        0xFFE0B000.toInt(), // yellow
        0xFF299438.toInt(), // green
        0xFF158FAD.toInt(), // teal
        0xFF246FE0.toInt(), // blue
        0xFF5B5BD6.toInt(), // indigo
        0xFFAF38EB.toInt(), // violet
        0xFFE05194.toInt(), // pink
        0xFF808080.toInt(), // grey
    )
    val Inbox = 0xFF246FE0.toInt()
}

data class Task(
    val id: String = newId(),
    val title: String,
    val description: String = "",
    val projectId: String = INBOX_ID,
    val labels: List<String> = emptyList(),
    val priority: Int = Priority.NONE,
    val due: LocalDate? = null,
    /** Only meaningful with a [due] date. */
    val time: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    /** Notify at the due time (only for tasks with a time). */
    val reminder: Boolean = true,
    /** Set on subtasks: the task they belong to. */
    val parentId: String? = null,
    /** A note this task came from or belongs to. */
    val noteId: String? = null,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("projectId", projectId)
        .put("labels", JSONArray(labels))
        .put("priority", priority)
        .put("due", due?.toString() ?: "")
        .put("time", time?.toString() ?: "")
        .put("repeat", repeat.name)
        .put("reminder", reminder)
        .put("parentId", parentId ?: "")
        .put("noteId", noteId ?: "")
        .put("done", done)
        .put("doneAt", doneAt)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.optString("id").ifEmpty { newId() },
            title = o.optString("title"),
            description = o.optString("description"),
            projectId = o.optString("projectId").ifEmpty { INBOX_ID },
            labels = o.optJSONArray("labels").strings(),
            priority = o.optInt("priority", Priority.NONE).coerceIn(Priority.P1, Priority.NONE),
            due = o.optString("due").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            time = o.optString("time").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
            repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
            reminder = o.optBoolean("reminder", true),
            parentId = o.optString("parentId").ifEmpty { null },
            noteId = o.optString("noteId").ifEmpty { null },
            done = o.optBoolean("done"),
            doneAt = o.optLong("doneAt"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

data class Project(
    val id: String = newId(),
    val name: String,
    val color: Int = ProjectColors.choices[6],
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("color", color).put("createdAt", createdAt)

    companion object {
        val Inbox = Project(INBOX_ID, "Inbox", ProjectColors.Inbox, 0L)

        fun fromJson(o: JSONObject) = Project(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            color = o.optInt("color", ProjectColors.choices[6]),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Notebook(
    val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt)

    companion object {
        val Default = Notebook(DEFAULT_NOTEBOOK_ID, "My notebook", 0L)

        fun fromJson(o: JSONObject) = Notebook(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Note(
    val id: String = newId(),
    val title: String = "",
    /** Light Markdown: headings, bullets, numbered lists, "[ ]" checklists, **bold**, *italic*, ~~strike~~. */
    val body: String = "",
    val notebookId: String = DEFAULT_NOTEBOOK_ID,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** When it was moved to the trash; 0 if it isn't there. */
    val trashedAt: Long = 0L,
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
    val trashed: Boolean get() = trashedAt != 0L

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("notebookId", notebookId)
        .put("tags", JSONArray(tags))
        .put("pinned", pinned)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("trashedAt", trashedAt)

    companion object {
        fun fromJson(o: JSONObject): Note {
            val created = o.optLong("createdAt", o.optLong("updatedAt"))
            return Note(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                body = o.optString("body"),
                notebookId = o.optString("notebookId").ifEmpty { DEFAULT_NOTEBOOK_ID },
                tags = o.optJSONArray("tags").strings(),
                pinned = o.optBoolean("pinned"),
                createdAt = created,
                updatedAt = o.optLong("updatedAt", created),
                trashedAt = o.optLong("trashedAt"),
            )
        }
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

enum class NoteSort(val label: String) { UPDATED("Date updated"), CREATED("Date created"), TITLE("Title") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsSunday: Boolean = false,
    val reminders: Boolean = true,
    val noteSort: NoteSort = NoteSort.UPDATED,
    /** Daybook has asked once for permission to post notifications. */
    val askedNotifications: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("weekStartsSunday", weekStartsSunday)
        .put("reminders", reminders)
        .put("noteSort", noteSort.name)
        .put("askedNotifications", askedNotifications)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                reminders = o.optBoolean("reminders", d.reminders),
                noteSort = runCatching { NoteSort.valueOf(o.optString("noteSort")) }.getOrDefault(d.noteSort),
                askedNotifications = o.optBoolean("askedNotifications", d.askedNotifications),
            )
        }
    }
}

/** Everything Daybook keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val notes: List<Note> = emptyList(),
    val notebooks: List<Notebook> = listOf(Notebook.Default),
    val settings: Settings = Settings(),
) {
    /** The Inbox first, then the user's projects in the order they were made. */
    val allProjects: List<Project> get() = listOf(Project.Inbox) + projects.sortedBy { it.createdAt }

    fun project(id: String?): Project = projects.firstOrNull { it.id == id } ?: Project.Inbox

    fun notebook(id: String?): Notebook = notebooks.firstOrNull { it.id == id } ?: notebooks.firstOrNull() ?: Notebook.Default

    fun task(id: String?): Task? = tasks.firstOrNull { it.id == id }

    fun note(id: String?): Note? = notes.firstOrNull { it.id == id }

    fun subtasks(parentId: String): List<Task> = tasks.filter { it.parentId == parentId }.sortedBy { it.createdAt }

    /** Every label used on a task, alphabetically. */
    val labels: List<String> get() = tasks.flatMap { it.labels }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    /** Every tag used on a note that isn't in the trash, alphabetically. */
    val tags: List<String> get() = notes.filter { !it.trashed }.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("tasks", JSONArray(tasks.map { it.toJson() }))
        .put("projects", JSONArray(projects.map { it.toJson() }))
        .put("notes", JSONArray(notes.map { it.toJson() }))
        .put("notebooks", JSONArray(notebooks.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject): Data {
            val notebooks = o.optJSONArray("notebooks").objects().map(Notebook::fromJson).ifEmpty { listOf(Notebook.Default) }
            val notes = o.optJSONArray("notes").objects().map(Note::fromJson).map { n ->
                // A note whose notebook is gone goes to the first notebook.
                if (notebooks.any { it.id == n.notebookId }) n else n.copy(notebookId = notebooks.first().id)
            }
            val projects = o.optJSONArray("projects").objects().map(Project::fromJson)
            val tasks = o.optJSONArray("tasks").objects().map(Task::fromJson).map { t ->
                if (t.projectId == INBOX_ID || projects.any { it.id == t.projectId }) t else t.copy(projectId = INBOX_ID)
            }
            // Daybook 1 had calendar events; they carry on as tasks with a date and time.
            val events = o.optJSONArray("events").objects().mapNotNull { e ->
                val date = e.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: return@mapNotNull null
                Task(
                    id = e.optString("id").ifEmpty { newId() },
                    title = e.optString("title"),
                    due = date,
                    time = e.optString("start").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                )
            }
            return Data(
                tasks = tasks + events,
                projects = projects,
                notes = notes,
                notebooks = notebooks,
                settings = Settings.fromJson(o.optJSONObject("settings")),
            )
        }

        /** What a new install starts with: a welcome note and three tasks that teach the basics. */
        fun firstRun(): Data {
            val now = System.currentTimeMillis()
            return Data(
                tasks = listOf(
                    Task(title = "Add a task with Quick Add: try “Pay rent friday 9am p1 @bills every month”", createdAt = now),
                    Task(title = "Swipe a task right to complete it, or left to delete it", createdAt = now + 1),
                    Task(title = "Tap a task to add a description, subtasks or a reminder", createdAt = now + 2),
                ),
                notes = listOf(
                    Note(
                        title = "Welcome to Daybook",
                        body = WELCOME_NOTE,
                        pinned = true,
                        createdAt = now,
                    ),
                ),
            )
        }

        private val WELCOME_NOTE = """
            Daybook keeps your **notes** and your **tasks** together.

            ## Notes
            - Group notes into notebooks and tag them
            - Use the toolbar for headings, lists and checklists
            - Pin the notes you use most

            ## Checklists
            [x] Open this note
            [ ] Tap a box to tick it off
            [ ] Add a task from a note with the ✓ button at the bottom

            ## Tasks
            Type naturally in Quick Add: dates like *tomorrow* or *next monday*, times like *9am*, **p1** to **p4** for priority, **#Project** and **@label**, and *every week* to repeat.
        """.trimIndent()
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }.filter { it.isNotBlank() }
