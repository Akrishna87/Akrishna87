package io.github.akrishna87.myvideos

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String) { VIDEOS("Videos"), FOLDERS("Folders") }

class VideoViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("ui", 0)
    private val watchProgress = WatchProgress(app)

    // ----- Library -----
    var videos by mutableStateOf<List<Video>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var progress by mutableStateOf<Map<String, Progress>>(emptyMap()); private set

    // ----- Navigation & UI -----
    var tab by mutableStateOf(Tab.entries.getOrElse(prefs.getInt("tab", 0)) { Tab.VIDEOS }); private set
    var sort by mutableStateOf(VideoSort.entries.getOrElse(prefs.getInt("sort", 0)) { VideoSort.NEWEST }); private set
    var searching by mutableStateOf(false); private set
    var query by mutableStateOf("")
    /** The folder page that's open, if any. */
    var openFolder by mutableStateOf<String?>(null); private set
    /** The video whose details sheet is showing. */
    var detailsFor by mutableStateOf<Video?>(null)

    val sorted: List<Video> by derivedStateOf { VideoGrouping.sort(videos, sort) }
    val visible: List<Video> by derivedStateOf { sorted.filter { it.matches(query) } }
    val folders: List<VideoFolder> by derivedStateOf {
        VideoGrouping.folders(videos).filter { f -> query.isBlank() || f.path.contains(query.trim(), true) || f.videos.any { it.matches(query) } }
    }
    val folderVideos: List<Video> by derivedStateOf { openFolder?.let { path -> sorted.filter { it.folder == path } }.orEmpty() }

    /** Started but not finished, most recently watched first. */
    val continueWatching: List<Video> by derivedStateOf {
        val byKey = videos.associateBy { it.key }
        progress.entries
            .filter { (_, p) -> p.resumeAt > 0 }
            .sortedByDescending { it.value.updatedAt }
            .mapNotNull { byKey[it.key] }
            .take(12)
    }

    private var observing = false
    private var refreshJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        // New downloads and recordings show up by themselves; wait for a burst of changes to settle.
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(1500)
                load()
            }
        }
    }

    fun onPermissionGranted() {
        if (!observing) {
            observing = true
            getApplication<Application>().contentResolver
                .registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        }
        viewModelScope.launch { load() }
    }

    fun rescan() {
        loading = true
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val list = withContext(Dispatchers.IO) {
            try {
                VideoRepository.loadVideos(getApplication())
            } catch (e: SecurityException) {
                emptyList()
            }
        }
        videos = list
        loading = false
        refreshProgress()
    }

    /** Called when coming back from the player, which saves progress as it plays. */
    fun refreshProgress() {
        progress = watchProgress.all()
    }

    fun progressOf(video: Video): Progress? = progress[video.key]

    fun selectTab(t: Tab) {
        tab = t
        openFolder = null
        prefs.edit().putInt("tab", t.ordinal).apply()
    }

    fun changeSort(s: VideoSort) {
        sort = s
        prefs.edit().putInt("sort", s.ordinal).apply()
    }

    fun startSearch() {
        searching = true
    }

    fun stopSearch() {
        searching = false
        query = ""
    }

    fun showFolder(path: String) {
        openFolder = path
    }

    /** Handles the system back gesture. Returns false when there's nothing to go back from. */
    fun back(): Boolean = when {
        detailsFor != null -> { detailsFor = null; true }
        searching -> { stopSearch(); true }
        openFolder != null -> { openFolder = null; true }
        tab != Tab.VIDEOS -> { selectTab(Tab.VIDEOS); true }
        else -> false
    }

    fun markWatched(video: Video) {
        watchProgress.markWatched(video.key, video.durationMs)
        refreshProgress()
    }

    fun markUnwatched(video: Video) {
        watchProgress.clear(video.key)
        refreshProgress()
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
    }
}
