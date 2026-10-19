package io.github.akrishna87.lumen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The photos and videos marked with a heart, kept in the app's own preferences so marking one is
 * instant (Android would ask for confirmation every time to change the media library's own flag).
 */
class Favorites(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun toggle(item: MediaItem) = set(listOf(item), item.id !in _ids.value)

    fun set(items: List<MediaItem>, favorite: Boolean) {
        val next = if (favorite) _ids.value + items.map { it.id } else _ids.value - items.map { it.id }.toSet()
        _ids.value = next
        prefs.edit().putStringSet(KEY, next.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
