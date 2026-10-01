package io.github.akrishna87.mytorrents.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.akrishna87.mytorrents.PendingAdd
import io.github.akrishna87.mytorrents.Screen
import io.github.akrishna87.mytorrents.TorrentsViewModel

@Composable
fun TorrentsRoot(vm: TorrentsViewModel) {
    AskForPermissions()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = vm.screen != Screen.List) { vm.back() }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.List -> ListScreen(vm)
                is Screen.Details -> DetailsScreen(vm, screen.id)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }
    if (vm.showAdd) AddSheet(vm)
    vm.pendingAdd?.let { ConfirmAddDialog(vm, it) }
}

/** "Add this torrent?" for links and files that came from another app. */
@Composable
private fun ConfirmAddDialog(vm: TorrentsViewModel, pending: PendingAdd) {
    AlertDialog(
        onDismissRequest = vm::cancelPendingAdd,
        title = { Text("Add this torrent?") },
        text = {
            Column {
                Text(pending.name, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(
                    "It will download into Download/Torrents. While it downloads, other people in the " +
                        "torrent can see your internet address.",
                    color = Palette.SubText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = vm::confirmPendingAdd) { Text("Download", color = Palette.Mint, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = vm::cancelPendingAdd) { Text("Cancel") } },
        containerColor = Palette.Elevated2,
    )
}

/**
 * Asks once for what the app needs: notifications (Android 13+) for progress and "finished" alerts,
 * and on Android 9 and older, storage access so downloads can go into the Download folder.
 * Saying no is fine: the app still works, just without those.
 */
@Composable
private fun AskForPermissions() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
    }
}
