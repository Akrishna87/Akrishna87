package io.github.akrishna87.daybook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Colors
import io.github.akrishna87.daybook.model.Note
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Notes as cards in two columns, pinned ones first. */
@Composable
fun NotesScreen(notes: List<Note>, padding: PaddingValues, onOpen: (Note) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = notes
        .filter { query.isBlank() || it.title.contains(query.trim(), true) || it.body.contains(query.trim(), true) }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                Text("Notes", style = MaterialTheme.typography.headlineMedium)
                if (notes.size > 3 || query.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        placeholder = { Text("Search notes") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Palette.Glass,
                            unfocusedContainerColor = Palette.Glass,
                            unfocusedBorderColor = Palette.GlassBorder,
                            focusedBorderColor = Color.White,
                            unfocusedPlaceholderColor = Palette.SubText,
                        ),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    if (query.isBlank()) "No notes yet. Tap New note to write one." else "No notes match \"${query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SubText,
                )
            }
        }
        items(shown, key = { it.id }) { note -> NoteCard(note, Modifier.animateItem()) { onOpen(note) } }
    }
}

@Composable
private fun NoteCard(note: Note, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        tint = if (note.color == 0) Palette.Glass else Color(note.color).copy(alpha = 0.38f),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (note.title.isNotBlank()) {
                Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (note.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(16.dp).padding(start = 2.dp), tint = Palette.SubText)
        }
        if (note.body.isNotBlank()) {
            Text(
                note.body,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.SubText,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(edited(note.updatedAt), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
    }
}

private fun edited(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Writing a note. It saves when you go back; a note left empty is thrown away. */
@Composable
fun NoteEditor(initial: Note, background: Color, onClose: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var body by remember { mutableStateOf(initial.body) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var color by remember { mutableIntStateOf(initial.color) }

    fun close() {
        if (title != initial.title || body != initial.body || pinned != initial.pinned || color != initial.color) {
            Store.saveNote(initial.copy(title = title.trim(), body = body.trimEnd(), pinned = pinned, color = color, updatedAt = System.currentTimeMillis()))
        }
        onClose()
    }
    BackHandler(onBack = ::close)

    val tint = if (color == 0) background else androidx.compose.ui.graphics.lerp(background, Color(color), 0.3f)
    Column(
        Modifier
            .fillMaxSize()
            .background(tint.deep(0.15f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { pinned = !pinned }) {
                Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin")
            }
            IconButton(onClick = { Store.deleteNote(initial.id); onClose() }) { Icon(Icons.Rounded.Delete, "Delete note") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            PlainField(title, { title = it }, "Title", MaterialTheme.typography.headlineSmall, singleLine = true)
            PlainField(body, { body = it }, "Note", MaterialTheme.typography.bodyLarge, Modifier.padding(top = 12.dp, bottom = 24.dp))
        }
        ColorChoices(
            Colors.choices, color, { color = it },
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            none = true,
        )
    }
}

@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        textStyle = style.copy(color = Color.White),
        singleLine = singleLine,
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Palette.Faint)
                inner()
            }
        },
    )
}
