package io.github.akrishna87.lumen

import android.Manifest
import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How much of the library the app may see. */
enum class Access { NONE, LIMITED, FULL }

data class LibraryState(
    val loaded: Boolean = false,
    /** Oldest first, the way the Library grid reads. */
    val items: List<MediaItem> = emptyList(),
    val trash: List<MediaItem> = emptyList(),
) {
    val albums: List<Album> by lazy { Grouping.albums(items) }
    val months: List<MonthGroup> by lazy { Grouping.months(items) }
    val videos: List<MediaItem> by lazy { items.filter { it.isVideo } }
    val screenshots: List<MediaItem> by lazy { items.filter { it.isScreenshot } }
    val panoramas: List<MediaItem> by lazy { items.filter { it.isPanorama } }
    val animated: List<MediaItem> by lazy { items.filter { it.isAnimated } }
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _access = MutableStateFlow(currentAccess())
    val access: StateFlow<Access> = _access.asStateFlow()

    private val favoriteStore = Favorites(app)
    val favorites: StateFlow<Set<Long>> = favoriteStore.ids

    private var loadJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(debounce = true)
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        refresh()
    }

    /** Re-reads the permission and the library, e.g. when the app comes back to the front. */
    fun refresh(debounce: Boolean = false) {
        _access.value = currentAccess()
        if (_access.value == Access.NONE) {
            _state.value = LibraryState(loaded = true)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(400) // a burst of changes (a camera burst, a big copy) loads once
            val (items, trash) = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                runCatching { MediaRepository.load(ctx) }.getOrDefault(emptyList()) to
                    runCatching { MediaRepository.loadTrash(ctx) }.getOrDefault(emptyList())
            }
            _state.value = LibraryState(loaded = true, items = items, trash = trash)
        }
    }

    fun toggleFavorite(item: MediaItem) = favoriteStore.toggle(item)

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) = favoriteStore.set(items, favorite)

    /**
     * Moves items to the trash (Android 11+) or deletes them. Returns the system confirmation to show,
     * or null when it's already done.
     */
    fun deleteRequest(items: List<MediaItem>): IntentSender? {
        val resolver = getApplication<Application>().contentResolver
        if (items.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= 30) return MediaStore.createTrashRequest(resolver, items.map { it.uri }, true).intentSender
        for (item in items) {
            try {
                resolver.delete(item.uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) return e.userAction.actionIntent.intentSender
                throw e
            }
        }
        refresh()
        return null
    }

    /** Puts trashed items back in the library (Android 11+). */
    fun recoverRequest(items: List<MediaItem>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30 && items.isNotEmpty()) {
            MediaStore.createTrashRequest(getApplication<Application>().contentResolver, items.map { it.uri }, false).intentSender
        } else null

    /** Deletes trashed items for good (Android 11+). */
    fun deleteForeverRequest(items: List<MediaItem>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30 && items.isNotEmpty()) {
            MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, items.map { it.uri }).intentSender
        } else null

    private fun currentAccess(): Access {
        val ctx = getApplication<Application>()
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO) -> Access.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.LIMITED
            Build.VERSION.SDK_INT >= 33 && (granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)) -> Access.LIMITED
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
            else -> Access.NONE
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    companion object {
        /** What to ask for on this version of Android. */
        val permissions: Array<String>
            get() = when {
                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
                Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
    }
}
