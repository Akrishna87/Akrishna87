package io.github.akrishna87.rssreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Net
import io.github.akrishna87.rssreader.data.NotAFeedException
import io.github.akrishna87.rssreader.data.Opml
import io.github.akrishna87.rssreader.data.Snapshot
import io.github.akrishna87.rssreader.data.Store
import io.github.akrishna87.rssreader.data.mergeArticles
import io.github.akrishna87.rssreader.data.normalizeUrl
import io.github.akrishna87.rssreader.data.rewriteKnownUrl
import io.github.akrishna87.rssreader.data.stableId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

sealed interface ReaderView {
    data object All : ReaderView
    data object Starred : ReaderView
    data class OneFeed(val feedId: String) : ReaderView
}

data class UiState(
    val loaded: Boolean = false,
    val feeds: List<Feed> = emptyList(),
    /** Newest first. */
    val articles: List<Article> = emptyList(),
    val view: ReaderView = ReaderView.All,
    val unreadOnly: Boolean = false,
    val refreshing: Boolean = false,
    val openArticleId: String? = null,
    val discoverOpen: Boolean = false,
    val manageOpen: Boolean = false,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(File(app.filesDir, "reader.json"))
    private val saveLock = Mutex()
    private var lastRefresh = 0L

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** A link shared into the app from another app, waiting to be added. */
    private val _shared = MutableStateFlow<String?>(null)
    val shared: StateFlow<String?> = _shared.asStateFlow()

    init {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) { store.load() }
            _state.update {
                it.copy(loaded = true, feeds = snap.feeds, articles = snap.articles.newestFirst(), unreadOnly = snap.unreadOnly)
            }
            refreshIfStale()
        }
    }

    // ---- Navigation -------------------------------------------------------

    fun setView(view: ReaderView) = _state.update { it.copy(view = view) }

    fun openArticle(id: String) {
        _state.update { s -> s.copy(openArticleId = id, articles = s.articles.map { if (it.id == id) it.copy(read = true) else it }) }
        persist()
    }

    fun closeArticle() = _state.update { it.copy(openArticleId = null) }

    fun openDiscover() = _state.update { it.copy(discoverOpen = true) }
    fun closeDiscover() = _state.update { it.copy(discoverOpen = false) }

    fun openManage() = _state.update { it.copy(manageOpen = true) }
    fun closeManage() = _state.update { it.copy(manageOpen = false) }

    fun setUnreadOnly(on: Boolean) {
        _state.update { it.copy(unreadOnly = on) }
        persist()
    }

    fun share(url: String) { _shared.value = url }
    fun clearShared() { _shared.value = null }

    // ---- Article state ----------------------------------------------------

    fun setRead(id: String, read: Boolean) {
        _state.update { s -> s.copy(articles = s.articles.map { if (it.id == id) it.copy(read = read) else it }) }
        persist()
    }

    fun toggleStar(id: String) {
        _state.update { s -> s.copy(articles = s.articles.map { if (it.id == id) it.copy(starred = !it.starred) else it }) }
        persist()
    }

    fun markAllRead(ids: Collection<String>) {
        val set = ids.toHashSet()
        var count = 0
        _state.update { s ->
            count = s.articles.count { it.id in set && !it.read }
            s.copy(articles = s.articles.map { if (it.id in set) it.copy(read = true) else it })
        }
        persist()
        if (count > 0) _messages.tryEmit("Marked $count as read")
    }

    // ---- Feeds ------------------------------------------------------------

    /** Subscribe to [raw] (a feed URL or a website). Returns an error message, or null on success. */
    suspend fun addFeed(raw: String, showFeed: Boolean = true): String? {
        val normalized = normalizeUrl(raw) ?: return "That doesn't look like a web address"
        val url = rewriteKnownUrl(normalized)
        if (_state.value.feeds.any { it.url == url }) return "You're already subscribed to this feed"
        return try {
            val (finalUrl, parsed) = withContext(Dispatchers.IO) { Net.loadFeed(url) }
            val id = stableId(finalUrl)
            _state.value.feeds.firstOrNull { it.id == id }?.let { return "You're already subscribed to ${it.title}" }
            val now = System.currentTimeMillis()
            val feed = Feed(id, finalUrl, parsed.title.ifBlank { hostOf(finalUrl) }, parsed.siteUrl, now)
            val merged = withContext(Dispatchers.Default) { mergeArticles(_state.value.articles, id, parsed, now).first }
            _state.update { s ->
                s.copy(
                    feeds = s.feeds + feed,
                    // Keep anything that changed while we were merging.
                    articles = (merged.filter { it.feedId == id } + s.articles.filter { it.feedId != id }).newestFirst(),
                    view = if (showFeed) ReaderView.OneFeed(id) else s.view,
                )
            }
            persist()
            _messages.tryEmit("Subscribed to ${feed.title}")
            null
        } catch (e: Exception) {
            friendly(e)
        }
    }

    fun removeFeed(id: String) {
        val title = _state.value.feeds.firstOrNull { it.id == id }?.title
        _state.update { s ->
            s.copy(
                feeds = s.feeds.filter { it.id != id },
                articles = s.articles.filter { it.feedId != id },
                view = if (s.view == ReaderView.OneFeed(id)) ReaderView.All else s.view,
            )
        }
        persist()
        if (title != null) _messages.tryEmit("Unsubscribed from $title")
    }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > 15 * 60_000) refreshAll(quiet = true)
    }

    fun refreshAll(quiet: Boolean = false) {
        if (_state.value.refreshing) return
        val feeds = _state.value.feeds
        if (feeds.isEmpty()) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val gate = Semaphore(4)
            val results = feeds.map { feed ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        try {
                            val parsed = Net.loadFeed(feed.url).second
                            val now = System.currentTimeMillis()
                            var added = 0
                            _state.update { s ->
                                val (merged, n) = mergeArticles(s.articles, feed.id, parsed, now)
                                added = n
                                s.copy(
                                    articles = merged.newestFirst(),
                                    feeds = s.feeds.map {
                                        if (it.id == feed.id) it.copy(lastFetched = now, error = null, title = parsed.title.ifBlank { it.title }) else it
                                    },
                                )
                            }
                            added
                        } catch (e: Exception) {
                            val msg = friendly(e)
                            _state.update { s -> s.copy(feeds = s.feeds.map { if (it.id == feed.id) it.copy(error = msg) else it }) }
                            -1
                        }
                    }
                }
            }.awaitAll()
            lastRefresh = System.currentTimeMillis()
            _state.update { it.copy(refreshing = false) }
            persist()
            val added = results.filter { it > 0 }.sum()
            val failed = results.count { it < 0 }
            if (!quiet || added > 0 || failed > 0) {
                var msg = if (added > 0) "$added new article${if (added == 1) "" else "s"}" else "No new articles"
                if (failed > 0) msg += " · $failed feed${if (failed == 1) "" else "s"} failed"
                _messages.tryEmit(msg)
            }
        }
    }

    fun importOpml(text: String) {
        val entries = try { Opml.parse(text) } catch (e: Exception) { emptyList() }
        val existing = _state.value.feeds.map { it.id }.toHashSet()
        val fresh = entries.distinctBy { it.url }.map { Feed(stableId(it.url), it.url, it.title.ifBlank { hostOf(it.url) }) }
            .filter { it.id !in existing }
        if (fresh.isEmpty()) {
            _messages.tryEmit(if (entries.isEmpty()) "No feeds found in that file" else "You already have all those feeds")
            return
        }
        _state.update { it.copy(feeds = it.feeds + fresh) }
        persist()
        _messages.tryEmit("Imported ${fresh.size} feed${if (fresh.size == 1) "" else "s"}, fetching…")
        refreshAll(quiet = true)
    }

    fun exportOpml(): String = Opml.export(_state.value.feeds)

    // ---- Helpers ----------------------------------------------------------

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                val s = _state.value
                store.save(Snapshot(s.feeds, s.articles, s.unreadOnly))
            }
        }
    }

    private fun List<Article>.newestFirst() = sortedByDescending { it.date }

    private fun friendly(e: Exception): String = when (e) {
        is NotAFeedException -> "Couldn't find a feed at that address"
        is UnknownHostException -> "Couldn't reach that site. Check the address or your connection"
        is SocketTimeoutException -> "The site took too long to answer"
        is IOException -> e.message ?: "Network error"
        else -> e.message ?: "Something went wrong"
    }
}

fun hostOf(url: String): String = try { URI(url).host?.removePrefix("www.") ?: url } catch (e: Exception) { url }
