package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AddFeedDialog(
    initial: String,
    initialFolder: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onAdd: suspend (url: String, folder: String) -> String?,
    onAdded: () -> Unit,
) {
    var url by remember { mutableStateOf(initial) }
    var folder by remember { mutableStateOf(initialFolder) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || url.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val err = onAdd(url, folder)
            busy = false
            if (err == null) onAdded() else error = err
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add a feed") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Feed or website address") },
                    placeholder = { Text("theverge.com") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error ?: "A website address works too, and the app finds its feed. YouTube channels, subreddits and GitHub projects work as well.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FolderPicker(folder, folders, enabled = !busy) { folder = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy && url.isNotBlank()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** A text box for the folder name, with the existing folders as one-tap chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderPicker(folder: String, folders: List<String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = folder,
            onValueChange = onChange,
            label = { Text("Folder (optional)") },
            placeholder = { Text("e.g. Tech, News") },
            singleLine = true,
            enabled = enabled,
        )
        if (folders.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                folders.forEach { f ->
                    FilterChip(
                        selected = f == folder.trim(),
                        onClick = { onChange(if (f == folder.trim()) "" else f) },
                        label = { Text(f) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
