package io.github.akrishna87.myvideos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.myvideos.Format
import io.github.akrishna87.myvideos.PlayerActivity
import io.github.akrishna87.myvideos.Tab
import io.github.akrishna87.myvideos.Video
import io.github.akrishna87.myvideos.VideoGrouping
import io.github.akrishna87.myvideos.VideoSort
import io.github.akrishna87.myvideos.VideoViewModel

private val VIDEO_PERMISSION =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasVideoPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, VIDEO_PERMISSION) == PackageManager.PERMISSION_GRANTED

@Composable
fun VideosApp(vm: VideoViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasVideoPermission(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) denied = true
    }
    // The person may have allowed access from system settings while we were in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasVideoPermission(context) }
    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }

    if (granted) {
        Shell(vm)
    } else {
        PermissionScreen(
            denied = denied,
            onAsk = { launcher.launch(VIDEO_PERMISSION) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onAsk: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1F4D), Palette.Background, Palette.Background))),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(112.dp).background(BrandGradient, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(56.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text("Your videos,\nright here.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                "My Videos plays the movies and videos already saved on this phone, straight from storage. " +
                    "Nothing is copied or uploaded.",
                textAlign = TextAlign.Center,
                color = Palette.SubText,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAsk, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                Text("Allow access to videos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (denied) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                    Text("Open settings")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "If the button above does nothing, open settings → Permissions → Photos and videos → Allow.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.SubText,
                )
            }
        }
    }
}

@Composable
private fun Shell(vm: VideoViewModel) {
    BackHandler(enabled = vm.searching || vm.openFolder != null || vm.tab != Tab.VIDEOS || vm.detailsFor != null) { vm.back() }
    // Pick up how far videos got after coming back from the player.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshProgress() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        val folder = vm.openFolder
        if (folder != null) {
            FolderPage(vm, folder)
        } else {
            Header(vm)
            TabChips(vm)
            when (vm.tab) {
                Tab.VIDEOS -> VideosTab(vm)
                Tab.FOLDERS -> FoldersTab(vm)
            }
        }
    }
    vm.detailsFor?.let { DetailsDialog(it) { vm.detailsFor = null } }
}

@Composable
private fun Header(vm: VideoViewModel) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.searching) {
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { focus.requestFocus() }
            TextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Search videos and folders") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Elevated2,
                    unfocusedContainerColor = Palette.Elevated2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(onClick = vm::stopSearch) { Icon(Icons.Rounded.Close, "Close search") }
        } else {
            Text("My Videos", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = vm::startSearch) { Icon(Icons.Rounded.Search, "Search") }
            SortMenu(vm)
            IconButton(onClick = vm::rescan) { Icon(Icons.Rounded.Refresh, "Rescan") }
        }
    }
}

@Composable
private fun SortMenu(vm: VideoViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            VideoSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    trailingIcon = { if (s == vm.sort) Icon(Icons.Rounded.Check, null, tint = Palette.Coral) },
                    onClick = {
                        open = false
                        vm.changeSort(s)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabChips(vm: VideoViewModel) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tab.entries.forEach { t ->
            FilterChip(
                selected = vm.tab == t,
                onClick = { vm.selectTab(t) },
                label = { Text(t.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.Coral,
                    selectedLabelColor = Color(0xFF1A0600),
                ),
            )
        }
    }
}

@Composable
private fun bottomPadding() =
    PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)

@Composable
private fun VideosTab(vm: VideoViewModel) {
    val context = LocalContext.current
    val list = vm.visible
    when {
        vm.loading && vm.videos.isEmpty() -> Loading()
        vm.videos.isEmpty() -> EmptyState("No videos yet", "Videos you record, download or copy onto this phone show up here by themselves.")
        list.isEmpty() -> EmptyState("No matches", "Nothing called “${vm.query.trim()}”.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding()) {
            val resume = vm.continueWatching
            if (!vm.searching && resume.isNotEmpty()) {
                item(key = "continue-header") { SectionHeader("Continue watching") }
                item(key = "continue") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(resume, key = { it.id }) { v ->
                            ContinueCard(v, vm.progressOf(v), 200.dp) {
                                // Carry on through the rest of its folder afterwards, like the next episode.
                                val queue = VideoGrouping.sort(vm.videos.filter { it.folder == v.folder }, VideoSort.NAME)
                                PlayerActivity.start(context, queue, queue.indexOf(v))
                            }
                        }
                    }
                }
            }
            item(key = "all-header") {
                SectionHeader(if (vm.searching && vm.query.isNotBlank()) "Results" else "All videos")
            }
            videoRows(list, vm, context, showFolder = true)
        }
    }
}

@Composable
private fun FoldersTab(vm: VideoViewModel) {
    val folders = vm.folders
    when {
        vm.loading && vm.videos.isEmpty() -> Loading()
        folders.isEmpty() -> EmptyState("No folders", if (vm.videos.isEmpty()) "There are no videos on this phone yet." else "No folder matches “${vm.query.trim()}”.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding()) {
            item { Spacer(Modifier.height(8.dp)) }
            items(folders, key = { it.path }) { f -> FolderRow(f) { vm.showFolder(f.path) } }
        }
    }
}

@Composable
private fun FolderPage(vm: VideoViewModel, path: String) {
    val context = LocalContext.current
    val list = vm.folderVideos
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(
            path.substringAfterLast('/').ifEmpty { "Internal storage" },
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
        SortMenu(vm)
    }
    if (list.isEmpty()) {
        EmptyState("This folder is empty", "Its videos have been moved or deleted.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding()) {
        item(key = "play-all") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { PlayerActivity.start(context, list, 0) }, shape = CircleShape) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play all", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "${list.size} ${if (list.size == 1) "video" else "videos"} · ${Format.minutes(list.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SubText,
                )
            }
        }
        videoRows(list, vm, context, showFolder = false)
    }
}

/** Tapping a video plays the list from there, so the next one starts when it ends. */
private fun LazyListScope.videoRows(list: List<Video>, vm: VideoViewModel, context: Context, showFolder: Boolean) {
    itemsIndexed(list, key = { _, v -> v.id }) { i, v ->
        VideoRow(
            video = v,
            progress = vm.progressOf(v),
            showFolder = showFolder,
            onClick = { PlayerActivity.start(context, list, i) },
            onPlayFromStart = { PlayerActivity.start(context, list, i, fromStart = true) },
            onMarkWatched = { vm.markWatched(v) },
            onMarkUnwatched = { vm.markUnwatched(v) },
            onDetails = { vm.detailsFor = v },
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(56.dp), tint = Palette.Faint)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Palette.SubText, textAlign = TextAlign.Center)
    }
}
