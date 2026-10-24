package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(title: String, onDismiss: () -> Unit, action: @Composable () -> Unit, content: @Composable () -> Unit) {
    val apple = LocalApple.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = apple.groupedBackground,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = {
            Box(Modifier.padding(top = 6.dp).size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(apple.tertiaryLabel))
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) { BarText("Cancel", onClick = onDismiss) }
                Text(title, style = AppleType.headline, color = apple.label, modifier = Modifier.align(Alignment.Center))
                Box(Modifier.align(Alignment.CenterEnd)) { action() }
            }
            content()
        }
    }
}

/** A one-line text prompt in a sheet. [onSubmit] returns an error to show, or null when done. */
@Composable
fun TextEntrySheet(
    title: String,
    placeholder: String,
    confirm: String,
    initial: String = "",
    footer: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    val apple = LocalApple.current
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        val err = onSubmit(text)
        if (err == null) onDismiss() else error = err
    }

    SheetFrame(title, onDismiss, action = { BarText(confirm, bold = true, enabled = text.isNotBlank()) { submit() } }) {
        GroupedSection(footer = error ?: footer) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppleTextField(text, { text = it; error = null }, placeholder, Modifier.weight(1f).focusRequester(focus))
            }
        }
        if (error != null) Text(error.orEmpty(), style = AppleType.footnote, color = apple.red, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

/** Pick which boards an article is saved to; tapping a row toggles it right away. */
@Composable
fun BoardSheet(
    boards: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onNewBoard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val apple = LocalApple.current
    SheetFrame("Save to Board", onDismiss, action = { BarText("Done", bold = true, onClick = onDismiss) }) {
        GroupedSection {
            boards.forEach { b ->
                AppleRow(
                    b,
                    leading = { Icon(AppIcons.Grid, null, tint = apple.secondaryLabel, modifier = Modifier.size(24.dp)) },
                    chevron = false,
                    onClick = { onToggle(b) },
                    trailing = { if (b in selected) Icon(AppIcons.Checkmark, "Saved", tint = apple.tint, modifier = Modifier.size(22.dp)) },
                )
            }
            AppleRow(
                "New Board…",
                leading = { Icon(AppIcons.Plus, null, tint = apple.tint, modifier = Modifier.size(24.dp)) },
                titleColor = apple.tint,
                chevron = false,
                showDivider = false,
                onClick = onNewBoard,
            )
        }
    }
}
