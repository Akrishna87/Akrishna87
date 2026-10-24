package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.rssreader.SyncStatus
import io.github.akrishna87.rssreader.data.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onLayout: (String) -> Unit,
    onTextSize: (Int) -> Unit,
    onTheme: (String) -> Unit,
    email: String?,
    status: SyncStatus,
    error: String?,
    lastSync: Long,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    val apple = LocalApple.current
    val layouts = listOf("magazine" to "Magazine", "cards" to "Cards", "title" to "Title only")
    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")

    AppleScreen(title = "Settings", snackbar = snackbar, backLabel = "Back", onBack = onBack) {
        item(key = "sync") {
            if (email == null) {
                GroupedSection(
                    header = "Sync",
                    footer = error ?: "Sign in with Google to keep your feeds, folders, boards, highlights and read state in step across your devices.",
                ) {
                    AppleRow(
                        if (status == SyncStatus.Syncing) "Signing in…" else "Sign in with Google",
                        titleColor = apple.tint, chevron = false, showDivider = false,
                        onClick = if (status == SyncStatus.Syncing) null else onSignIn,
                    )
                }
            } else {
                val line = when (status) {
                    SyncStatus.Syncing -> "Syncing…"
                    SyncStatus.Error -> error ?: "Sync failed"
                    else -> if (lastSync > 0) "Synced " + relativeTime(lastSync).let { if (it == "Now") "just now" else "$it ago" } else "Signed in"
                }
                GroupedSection(header = "Sync", footer = line) {
                    AppleRow(email, subtitle = null, chevron = false, titleColor = apple.label)
                    AppleRow("Sync Now", titleColor = apple.tint, chevron = false, onClick = if (status == SyncStatus.Syncing) null else onSyncNow)
                    AppleRow("Sign Out", titleColor = apple.red, chevron = false, showDivider = false, onClick = onSignOut)
                }
            }
        }
        item(key = "layout") {
            GroupedSection(header = "Article list", footer = "You can also change the layout from the ⋮ menu above any list.") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SegmentedControl(layouts.map { it.second }, layouts.indexOfFirst { it.first == settings.layout }.coerceAtLeast(0)) { onLayout(layouts[it].first) }
                }
            }
        }
        item(key = "theme") {
            GroupedSection(header = "Appearance") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SegmentedControl(themes.map { it.second }, themes.indexOfFirst { it.first == settings.theme }.coerceAtLeast(0)) { onTheme(themes[it].first) }
                }
            }
        }
        item(key = "text") {
            GroupedSection(header = "Reading text size", footer = "${settings.textSize} pt. This is the size used for article text.") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarText("A−", enabled = settings.textSize > 14) { onTextSize(settings.textSize - 1) }
                    Text(
                        "The quick brown fox jumps over the lazy dog.",
                        style = AppleType.body.copy(fontSize = settings.textSize.sp),
                        color = apple.label,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    BarText("A+", enabled = settings.textSize < 26) { onTextSize(settings.textSize + 1) }
                }
            }
        }
    }
}
