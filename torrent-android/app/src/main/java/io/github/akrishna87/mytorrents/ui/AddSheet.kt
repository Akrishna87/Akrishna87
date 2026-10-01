package io.github.akrishna87.mytorrents.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.mytorrents.TorrentsViewModel

/** "Add a torrent": paste a magnet link, or pick a .torrent file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: TorrentsViewModel) {
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addTorrentFile(uri)
    }
    // A magnet link that was just copied is almost certainly the one to add.
    LaunchedEffect(Unit) {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.startsWith("magnet:?")) link = clip
    }

    ModalBottomSheet(
        onDismissRequest = { vm.showAdd = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Elevated,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add a torrent", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Magnet link" },
                placeholder = { Text("Magnet link") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { link = it.trim() } }) {
                        Icon(Icons.Rounded.ContentPaste, "Paste")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank()) vm.addMagnet(link) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Mint, cursorColor = Palette.Mint),
            )
            Button(
                onClick = { vm.addMagnet(link) },
                enabled = link.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Mint, contentColor = Palette.Background),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Download", fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("or", color = Palette.Faint)
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { pickFile.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Rounded.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("Open a .torrent file")
            }
            Text(
                "Only download things you have the right to, such as free software, public-domain " +
                    "films and books, or Creative Commons music.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Faint,
            )
        }
    }
}
