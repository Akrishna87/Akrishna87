package io.github.akrishna87.mytorrents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akrishna87.mytorrents.TorrentsViewModel

private val SPEEDS = listOf(0, 100, 500, 1024, 5 * 1024, 10 * 1024)

private fun speedLabel(kb: Int) = when {
    kb == 0 -> "No limit"
    kb < 1024 -> "$kb KB/s"
    else -> "${kb / 1024} MB/s"
}

@Composable
fun SettingsScreen(vm: TorrentsViewModel) {
    val s = vm.settings
    val wifiOnly by s.wifiOnly.collectAsState()
    val seed by s.seedWhenFinished.collectAsState()
    val down by s.downloadLimitKb.collectAsState()
    val up by s.uploadLimitKb.collectAsState()
    var folder by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { folder = vm.downloadFolder() }
    var picking by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Toggle("Wi-Fi only", "Pause downloads while on mobile data", wifiOnly, s::setWifiOnly)
        Toggle(
            "Keep sharing after downloading",
            "Others download from you too, which keeps torrents alive, but uses more data and battery",
            seed,
            s::setSeedWhenFinished,
        )
        Item("Download speed limit", speedLabel(down)) { picking = "down" }
        Item("Upload speed limit", speedLabel(up)) { picking = "up" }
        Item("Saved in", folder.ifEmpty { "…" }) {}
        Text(
            "Only download things you have the right to share. When you download a torrent, " +
                "other people can see your internet address.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Faint,
            modifier = Modifier.padding(20.dp),
        )
    }

    picking?.let { which ->
        SpeedDialog(
            title = if (which == "down") "Download speed limit" else "Upload speed limit",
            current = if (which == "down") down else up,
            onDismiss = { picking = null },
        ) { kb ->
            if (which == "down") s.setDownloadLimitKb(kb) else s.setUploadLimitKb(kb)
            picking = null
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Mint, checkedThumbColor = Palette.Background),
        )
    }
}

@Composable
private fun Item(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
    }
}

@Composable
private fun SpeedDialog(title: String, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SPEEDS.forEach { kb ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = kb == current, onClick = { onPick(kb) }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = kb == current, onClick = { onPick(kb) }, colors = RadioButtonDefaults.colors(selectedColor = Palette.Mint))
                        Text(speedLabel(kb))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = Palette.Elevated2,
    )
}
