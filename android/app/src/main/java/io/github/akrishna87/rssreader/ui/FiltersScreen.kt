package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun FiltersScreen(
    mute: List<String>,
    highlight: List<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAddMute: (String) -> Unit,
    onRemoveMute: (String) -> Unit,
    onAddHighlight: (String) -> Unit,
    onRemoveHighlight: (String) -> Unit,
) {
    AppleScreen(title = "Keyword Filters", snackbar = snackbar, backLabel = "Feeds", onBack = onBack) {
        termSection(
            key = "mute",
            header = "Mute",
            footer = "Articles mentioning these are hidden. Starred articles are never hidden, and “Show” at the top of a list reveals muted ones.",
            placeholder = "Add a word, e.g. sponsored",
            terms = mute,
            onAdd = onAddMute,
            onRemove = onRemoveMute,
        )
        termSection(
            key = "highlight",
            header = "Highlight",
            footer = "Articles mentioning these get a ★ tag and appear under Keyword Alerts. Matching uses titles and opening text, whole words, ignoring capitals. Separate several words with commas.",
            placeholder = "Add a word, e.g. Azure",
            terms = highlight,
            onAdd = onAddHighlight,
            onRemove = onRemoveHighlight,
        )
    }
}

private fun LazyListScope.termSection(
    key: String,
    header: String,
    footer: String,
    placeholder: String,
    terms: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    item(key = key) {
        val apple = LocalApple.current
        var input by rememberSaveable { mutableStateOf("") }
        fun submit() {
            if (input.isBlank()) return
            onAdd(input)
            input = ""
        }
        GroupedSection(header = header, footer = footer) {
            terms.forEach { t ->
                AppleRow(
                    t,
                    chevron = false,
                    leading = {
                        Icon(
                            AppIcons.MinusCircle,
                            "Remove $t",
                            tint = apple.red,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    dividerStart = 16.dp,
                    onClick = { onRemove(t) },
                )
            }
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppleTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = placeholder,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                BarText("Add", bold = true, enabled = input.isNotBlank()) { submit() }
            }
        }
    }
}
