package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedSheet(
    initial: String,
    initialFolder: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onAdd: suspend (url: String, folder: String) -> String?,
    onAdded: () -> Unit,
) {
    val apple = LocalApple.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initial) }
    var folder by remember { mutableStateOf(initialFolder) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun close() = scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }

    fun submit() {
        if (busy || url.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val err = onAdd(url, folder)
            busy = false
            if (err == null) {
                sheet.hide()
                onAdded()
            } else {
                error = err
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheet,
        containerColor = apple.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = {
            Box(Modifier.padding(top = 6.dp).size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(apple.tertiaryLabel))
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { BarText("Cancel", enabled = !busy) { close() } }
                Text("Add Feed", style = AppleType.headline, color = apple.label, modifier = Modifier.align(Alignment.Center))
                Box(Modifier.align(Alignment.CenterEnd)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), color = apple.tint, strokeWidth = 2.dp)
                    } else {
                        BarText("Add", bold = true, enabled = url.isNotBlank()) { submit() }
                    }
                }
            }
            GroupedSection(
                footer = if (error == null) "A website address works too, and Vellum finds its feed. YouTube channels, subreddits and GitHub projects work as well." else null,
            ) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppleTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = "Feed or website address",
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
            }
            if (error != null) {
                Text(error.orEmpty(), style = AppleType.footnote, color = apple.red, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp))
            }
            FolderPicker(folder, folders, enabled = !busy) { folder = it }
        }
    }
}

/** "Folder" section: a text row for a new name plus the existing folders as tappable capsules. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderPicker(folder: String, folders: List<String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    val apple = LocalApple.current
    GroupedSection(header = "Folder", footer = if (folders.isEmpty()) "Optional. Feeds with the same folder are grouped together." else null) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppleTextField(value = folder, onValueChange = onChange, placeholder = "No folder", enabled = enabled, modifier = Modifier.weight(1f))
        }
    }
    if (folders.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            folders.forEach { f ->
                val selected = f == folder.trim()
                Text(
                    f,
                    style = AppleType.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = if (selected) androidx.compose.ui.graphics.Color.White else apple.label,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) apple.tint else apple.cell)
                        .clickable(enabled = enabled) { onChange(if (selected) "" else f) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}
