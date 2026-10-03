package io.github.akrishna87.daybook.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Backup
import io.github.akrishna87.daybook.Reminders
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.INBOX_ID
import io.github.akrishna87.daybook.model.Markdown
import io.github.akrishna87.daybook.model.Project
import io.github.akrishna87.daybook.model.ProjectColors
import io.github.akrishna87.daybook.model.ThemeMode
import java.time.LocalDate

/** The Inbox, projects, labels, completed tasks, the notes trash and settings. */
@Composable
fun BrowseScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    var newProject by remember { mutableStateOf(false) }
    val open = data.tasks.filter { !it.done && it.parentId == null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader("Browse") {
                SearchAction { nav.push(Page.Search) }
                IconButton(onClick = { nav.push(Page.SettingsPage) }, Modifier.semantics { contentDescription = "Settings" }) { Icon(Icons.Rounded.Settings, null) }
            }
        }
        item {
            ListRow(
                "Inbox",
                leading = { Icon(Icons.Rounded.Inbox, null, tint = Color(ProjectColors.Inbox)) },
                trailing = { Count(open.count { it.projectId == INBOX_ID }) },
                onClick = { nav.push(Page.ProjectPage(INBOX_ID)) },
            )
        }
        item {
            SectionHeader("My projects") {
                IconButton(onClick = { newProject = true }, Modifier.semantics { contentDescription = "Add project" }) { Icon(Icons.Rounded.Add, null) }
            }
        }
        if (data.projects.isEmpty()) {
            item {
                Text(
                    "Group tasks into projects, like Work or Home. Tap + to add one.",
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(data.projects.sortedBy { it.createdAt }, key = { it.id }) { p ->
            ListRow(
                p.name,
                leading = { Text("#", style = MaterialTheme.typography.titleLarge, color = Color(p.color)) },
                trailing = { Count(open.count { it.projectId == p.id }) },
                onClick = { nav.push(Page.ProjectPage(p.id)) },
            )
        }
        if (data.labels.isNotEmpty()) {
            item { SectionHeader("Labels") }
            items(data.labels, key = { "label-$it" }) { l ->
                ListRow(
                    l,
                    leading = { Icon(Icons.Rounded.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = { Count(open.count { t -> t.labels.any { it.equals(l, true) } }) },
                    onClick = { nav.push(Page.LabelPage(l)) },
                )
            }
        }
        item { SectionHeader("More") }
        item {
            ListRow(
                "Completed",
                leading = { Icon(Icons.Rounded.CheckCircle, null, tint = LocalExtra.current.complete) },
                trailing = { Count(data.tasks.count { it.done }) },
                onClick = { nav.push(Page.Completed) },
            )
        }
        item {
            ListRow(
                "Trash",
                leading = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailing = { Count(data.notes.count { it.trashed }) },
                onClick = { nav.push(Page.Trash) },
            )
        }
        item {
            ListRow(
                "Settings",
                leading = { Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { nav.push(Page.SettingsPage) },
            )
        }
    }
    if (newProject) {
        NameDialog(
            title = "New project",
            confirm = "Add",
            colors = ProjectColors.choices,
            initialColor = NewProjectColor,
            onSave = { name, color ->
                val p = Project(name = name, color = color)
                Store.saveProject(p)
                nav.push(Page.ProjectPage(p.id))
            },
            onDismiss = { newProject = false },
        )
    }
}

/** Searches task titles, descriptions and labels, and note titles, text and tags. */
@Composable
fun SearchPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.text.trim()
    val tasks = if (q.isEmpty()) emptyList() else data.tasks.filter { t ->
        t.title.contains(q, true) || t.description.contains(q, true) || t.labels.any { it.contains(q.removePrefix("@"), true) }
    }.sortedWith(compareBy<io.github.akrishna87.daybook.model.Task>({ it.done }).then(Dates.taskOrder))
    val notes = if (q.isEmpty()) emptyList() else sortNotes(
        data.notes.filter { n ->
            !n.trashed && (n.title.contains(q, true) || n.body.contains(q, true) || n.tags.any { it.contains(q.removePrefix("#"), true) })
        },
        data.settings.noteSort,
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        run {
            PageBar(onBack = { nav.pop() }, leading = {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                PlainField(
                    value = query,
                    onChange = { query = it },
                    placeholder = "Search tasks and notes",
                    description = "Search",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(10f).padding(start = 12.dp).focusRequester(focus),
                    singleLine = true,
                )
            }) {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }, Modifier.semantics { contentDescription = "Clear search" }) { Icon(Icons.Rounded.Close, null) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (q.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Search everything", "Find tasks by title, description or @label, and notes by title, text or #tag.") }
            } else if (tasks.isEmpty() && notes.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches “$q”.") }
            }
            if (tasks.isNotEmpty()) {
                item { SectionHeader("Tasks · ${tasks.size}") }
                items(tasks, key = { "t-" + it.id }) { t -> TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
            }
            if (notes.isNotEmpty()) {
                item { SectionHeader("Notes · ${notes.size}") }
                items(notes, key = { "n-" + it.id }) { n -> NoteRow(n, data, showNotebook = true, today = today) { nav.push(Page.NotePage(n.id)) } }
            }
        }
    }
}

/** Notes moved to the trash: restore them, delete them for good, or empty the trash. */
@Composable
fun TrashPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var emptying by remember { mutableStateOf(false) }
    val trashed = data.notes.filter { it.trashed }.sortedByDescending { it.trashedAt }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Trash", onBack = { nav.pop() }) {
            if (trashed.isNotEmpty()) TextButton(onClick = { emptying = true }) { Text("Empty") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (trashed.isEmpty()) item { EmptyState(Icons.Rounded.Delete, "Trash is empty", "Notes you delete stay here until you empty the trash.") }
            items(trashed, key = { it.id }) { n ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Markdown.plain(n.body, 120).replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("Deleted ${Dates.shortDate(n.trashedAt, today)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { Store.restoreNote(n.id) }, Modifier.semantics { contentDescription = "Restore ${n.title}" }) {
                            Icon(Icons.Rounded.RestoreFromTrash, null)
                        }
                        IconButton(onClick = { Store.deleteNoteForever(n.id) }, Modifier.semantics { contentDescription = "Delete ${n.title} forever" }) {
                            Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (emptying) {
        ConfirmDialog("Empty the trash?", "${trashed.size} notes are deleted for good.", "Empty trash", onConfirm = { Store.emptyTrash() }, onDismiss = { emptying = false })
    }
}

/** Theme, week start, reminders, backups and the version. */
@Composable
fun SettingsPage(data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val s = data.settings
    var refresh by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf<Data?>(null) }
    val canNotify = remember(refresh) { Reminders.canNotify(context) }
    val canExact = remember(refresh) { Reminders.canScheduleExact(context) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.message(if (Backup.export(context, uri)) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = Backup.read(context, uri)
            if (d == null) actions.message("That file isn't a Daybook backup") else restoring = d
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Settings", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Theme") }
            items(ThemeMode.entries) { mode ->
                ListRow(
                    mode.label,
                    leading = { RadioButton(selected = s.theme == mode, onClick = null) },
                    onClick = { Store.updateSettings { it.copy(theme = mode) } },
                    modifier = Modifier.semantics { contentDescription = "Theme: ${mode.label}" },
                )
            }
            item { SectionHeader("Calendar") }
            item {
                ListRow(
                    "Week starts on Sunday",
                    trailing = { Switch(checked = s.weekStartsSunday, onCheckedChange = { on -> Store.updateSettings { it.copy(weekStartsSunday = on) } }) },
                    onClick = { Store.updateSettings { it.copy(weekStartsSunday = !it.weekStartsSunday) } },
                )
            }
            item { SectionHeader("Reminders") }
            item {
                ListRow(
                    "Remind me when tasks are due",
                    detail = "For tasks with a time",
                    trailing = { Switch(checked = s.reminders, onCheckedChange = { on -> Store.updateSettings { it.copy(reminders = on) } }) },
                    onClick = { Store.updateSettings { it.copy(reminders = !it.reminders) } },
                )
            }
            if (s.reminders && !canNotify) {
                item {
                    ListRow(
                        "Notifications are off",
                        detail = "Tap to allow them, or reminders can't appear",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !s.askedNotifications) {
                                Store.updateSettings { it.copy(askedNotifications = true) }
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                    )
                }
            }
            if (s.reminders && !canExact && Build.VERSION.SDK_INT >= 31) {
                item {
                    ListRow(
                        "Reminders may be a few minutes late",
                        detail = "Tap to allow exact alarms for on-time reminders",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        },
                    )
                }
            }
            item { SectionHeader("Backup") }
            item {
                ListRow(
                    "Export backup",
                    detail = "Save every task and note to a file",
                    onClick = { exporter.launch("daybook-backup-${LocalDate.now()}.json") },
                )
            }
            item {
                ListRow(
                    "Restore from backup",
                    detail = "Replace everything with a backup file",
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            item { SectionHeader("About") }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                ListRow("Daybook", detail = "Version $version · Notes and tasks, on your phone only")
            }
        }
    }
    restoring?.let { backup ->
        ConfirmDialog(
            "Restore this backup?",
            "It has ${backup.tasks.size} tasks and ${backup.notes.size} notes. Everything in Daybook now is replaced.",
            "Restore",
            onConfirm = {
                Store.replaceAll(backup)
                actions.message("Backup restored")
            },
            onDismiss = { restoring = null },
        )
    }
}
