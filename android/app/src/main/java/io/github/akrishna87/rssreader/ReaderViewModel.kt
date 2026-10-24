package io.github.akrishna87.rssreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Filters
import io.github.akrishna87.rssreader.data.Highlight
import io.github.akrishna87.rssreader.data.Net
import io.github.akrishna87.rssreader.data.NotAFeedException
import io.github.akrishna87.rssreader.data.Opml
import io.github.akrishna87.rssreader.data.Settings
import io.github.akrishna87.rssreader.data.Snapshot
import io.github.akrishna87.rssreader.data.Store
import io.github.akrishna87.rssreader.data.SyncMerge
import io.github.akrishna87.rssreader.data.mergeArticles
import io.github.akrishna87.rssreader.data.normalizeUrl
import io.github.akrishna87.rssreader.data.rewriteKnownUrl
import io.github.akrishna87.rssreader.data.stableId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Articles from the last day. */
    data object Today : ReaderView
    data object All : ReaderView
    /** Read Later. */
    data object Starred : ReaderView
    /** Everything from feeds marked as priority. */
    data object Priority : ReaderView
    data class OneFeed(val feedId: String) : ReaderView
    data class Folder(val name: String) : ReaderView
    data class Board(val name: String) : ReaderView
    /** Articles with passages the reader highlighted. */
    data object Notes : ReaderView
    /** Articles matching the keyword alerts. */
    data object Alerts : ReaderView
}

data class UiState(
    val loaded: Boolean = false,
    val feeds: List<Feed> = emptyList(),
    /** Newest first. */
    val articles: List<Article> = emptyList(),
    val view: ReaderView = ReaderView.Today,
    val unreadOnly: Boolean = false,
    val refreshing: Boolean = false,
    val openArticleId: String? = null,
    val discoverOpen: Boolean = false,
    val manageOpen: Boolean = false,
    val filtersOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val boards: List<String> = emptyList(),
    val settings: Settings = Settings(),
    val deletedFeeds: Map<String, Long> = emptyMap(),
    val boardTs: Map<String, Long> = emptyMap(),
    val deletedBoards: Map<String, Long> = emptyMap(),
    val prefsTs: Long = 0,
    val syncEmail: String? = null,
    val syncStatus: SyncStatus = SyncStatus.Off,
    val syncError: String? = null,
    val lastSync: Long = 0,
    val mute: List<String> = emptyList(),
    val highlight: List<String> = emptyList(),
    val collapsed: Set<String> = emptySet(),
    /** Show muted articles in the list (dimmed) instead of hiding them. */
    val showMuted: Boolean = false,
)

enum class SyncStatus { Off, Syncing, Synced, Error }

private fun UiState.toSnapshot() = Snapshot(
    feeds, articles, unreadOnly, mute, highlight, collapsed, boards, settings, deletedFeeds, boardTs, deletedBoards, prefsTs,
)

private fun UiState.withSnapshot(s: Snapshot) = copy(
    feeds = s.feeds, articles = s.articles.sortedByDescending { it.date }, mute = s.mute, highlight = s.highlight, boards = s.boards,
    settings = s.settings, deletedFeeds = s.deletedFeeds, boardTs = s.boardTs, deletedBoards = s.deletedBoards, prefsTs = s.prefsTs,
    // A removed feed can't stay selected.
    view = view.let { v -> if (v is ReaderView.OneFeed && s.feeds.none { it.id == v.feedId }) ReaderView.Today else v },
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(File(app.filesDir, "reader.json"))
    private val cloud = Cloud(app.getSharedPreferences("sync", 0))
    private val syncLock = Mutex()
    private var syncJob: Job? = null
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
                it.copy(
                    loaded = true, feeds = snap.feeds, articles = snap.articles.newestFirst(), unreadOnly = snap.unreadOnly,
                    mute = snap.mute, highlight = snap.highlight, collapsed = snap.collapsed,
                    boards = snap.boards, settings = snap.settings, deletedFeeds = snap.deletedFeeds, boardTs = snap.boardTs,
                    deletedBoards = snap.deletedBoards, prefsTs = snap.prefsTs,
                    syncEmail = cloud.session()?.email, syncStatus = if (cloud.session() != null) SyncStatus.Synced else SyncStatus.Off,
                )
            }
            refreshIfStale()
            syncSoon(0)
        }
    }

    // ---- Navigation -------------------------------------------------------

    fun setView(view: ReaderView) = _state.update { it.copy(view = view, showMuted = false) }

    fun openArticle(id: String) {
        val now = System.currentTimeMillis()
        _state.update { s -> s.copy(openArticleId = id, articles = s.articles.map { if (it.id == id && !it.read) it.copy(read = true, mod = now) else it }) }
        persist()
    }

    fun closeArticle() = _state.update { it.copy(openArticleId = null) }

    fun openDiscover() = _state.update { it.copy(discoverOpen = true) }
    fun closeDiscover() = _state.update { it.copy(discoverOpen = false) }

    fun openManage() = _state.update { it.copy(manageOpen = true) }
    fun closeManage() = _state.update { it.copy(manageOpen = false) }

    fun openSettings() = _state.update { it.copy(settingsOpen = true) }
    fun closeSettings() = _state.update { it.copy(settingsOpen = false) }

    fun openFilters() = _state.update { it.copy(filtersOpen = true) }
    fun closeFilters() = _state.update { it.copy(filtersOpen = false) }

    fun setShowMuted(on: Boolean) = _state.update { it.copy(showMuted = on) }

    fun toggleFolder(name: String) {
        _state.update { s -> s.copy(collapsed = if (name in s.collapsed) s.collapsed - name else s.collapsed + name) }
        persist()
    }

    // ---- Keyword filters ----------------------------------------------------

    fun addMute(input: String) { _state.update { it.copy(mute = Filters.addTerms(it.mute, input), prefsTs = now()) }; persist() }
    fun removeMute(term: String) { _state.update { it.copy(mute = it.mute - term, prefsTs = now()) }; persist() }
    fun addHighlight(input: String) { _state.update { it.copy(highlight = Filters.addTerms(it.highlight, input), prefsTs = now()) }; persist() }
    fun removeHighlight(term: String) {
        _state.update { s ->
            val list = s.highlight - term
            s.copy(highlight = list, prefsTs = now(), view = if (list.isEmpty() && s.view == ReaderView.Alerts) ReaderView.Today else s.view)
        }
        persist()
    }

    fun setUnreadOnly(on: Boolean) {
        _state.update { it.copy(unreadOnly = on) }
        persist()
    }

    fun share(url: String) { _shared.value = url }
    fun clearShared() { _shared.value = null }

    // ---- Article state ----------------------------------------------------

    fun setRead(id: String, read: Boolean) = updateArticle(id) { it.copy(read = read) }

    fun toggleStar(id: String) = updateArticle(id) { it.copy(starred = !it.starred) }

    private fun updateArticle(id: String, change: (Article) -> Article) {
        val now = now()
        _state.update { s -> s.copy(articles = s.articles.map { if (it.id == id) change(it).copy(mod = now) else it }) }
        persist()
    }

    // ---- Boards ---------------------------------------------------------------

    /** Create a board; returns false if the name is blank or already used. */
    fun createBoard(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || _state.value.boards.any { it.equals(n, true) }) return false
        _state.update { it.copy(boards = it.boards + n, boardTs = it.boardTs + (n to now()), deletedBoards = it.deletedBoards - n) }
        persist()
        return true
    }

    fun deleteBoard(name: String) {
        _state.update { s ->
            val now = now()
            s.copy(
                boards = s.boards - name,
                boardTs = s.boardTs - name,
                deletedBoards = s.deletedBoards + (name to now),
                articles = s.articles.map { if (name in it.boards) it.copy(boards = it.boards - name, mod = now) else it },
                view = if (s.view == ReaderView.Board(name)) ReaderView.Today else s.view,
            )
        }
        persist()
        _messages.tryEmit("Deleted board $name")
    }

    fun toggleBoard(articleId: String, board: String) =
        updateArticle(articleId) { a -> a.copy(boards = if (board in a.boards) a.boards - board else a.boards + board) }

    // ---- Highlights and notes -------------------------------------------------

    fun addTextHighlight(articleId: String, text: String, note: String = "") {
        val t = text.trim()
        if (t.isEmpty()) return
        updateArticle(articleId) { a ->
            val rest = a.highlights.filter { it.text != t }
            a.copy(highlights = rest + Highlight(t, note.trim(), System.currentTimeMillis()))
        }
    }

    fun removeTextHighlight(articleId: String, text: String) =
        updateArticle(articleId) { a -> a.copy(highlights = a.highlights.filter { it.text != text }) }

    // ---- Settings ---------------------------------------------------------------

    fun setLayout(layout: String) { _state.update { it.copy(settings = it.settings.copy(layout = layout), prefsTs = now()) }; persist() }
    fun setTextSize(size: Int) { _state.update { it.copy(settings = it.settings.copy(textSize = size.coerceIn(14, 26)), prefsTs = now()) }; persist() }
    fun setTheme(theme: String) { _state.update { it.copy(settings = it.settings.copy(theme = theme), prefsTs = now()) }; persist() }

    /** Mark as read the articles in [ids] that are older than [cutoff]. */
    fun markOlderRead(ids: Collection<String>, cutoff: Long) {
        val set = ids.toHashSet()
        var count = 0
        _state.update { s ->
            count = s.articles.count { it.id in set && !it.read && it.date < cutoff }
            val now = now()
            s.copy(articles = s.articles.map { if (it.id in set && it.date < cutoff && !it.read) it.copy(read = true, mod = now) else it })
        }
        persist()
        _messages.tryEmit(if (count > 0) "Marked $count as read" else "Nothing older to mark")
    }

    fun markAllRead(ids: Collection<String>) {
        val set = ids.toHashSet()
        var count = 0
        _state.update { s ->
            count = s.articles.count { it.id in set && !it.read }
            val now = now()
            s.copy(articles = s.articles.map { if (it.id in set && !it.read) it.copy(read = true, mod = now) else it })
        }
        persist()
        if (count > 0) _messages.tryEmit("Marked $count as read")
    }

    // ---- Feeds ------------------------------------------------------------

    /** Subscribe to [raw] (a feed URL or a website). Returns an error message, or null on success. */
    suspend fun addFeed(raw: String, showFeed: Boolean = true, folder: String = ""): String? {
        val normalized = normalizeUrl(raw) ?: return "That doesn't look like a web address"
        val url = rewriteKnownUrl(normalized)
        if (_state.value.feeds.any { it.url == url }) return "You're already subscribed to this feed"
        return try {
            val (finalUrl, parsed) = withContext(Dispatchers.IO) { Net.loadFeed(url) }
            val id = stableId(finalUrl)
            _state.value.feeds.firstOrNull { it.id == id }?.let { return "You're already subscribed to ${it.name}" }
            val now = System.currentTimeMillis()
            val feed = Feed(id, finalUrl, parsed.title.ifBlank { hostOf(finalUrl) }, parsed.siteUrl, now, folder = folder.trim(), ts = now)
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
            _messages.tryEmit("Subscribed to ${feed.name}")
            null
        } catch (e: Exception) {
            friendly(e)
        }
    }

    fun setPriority(id: String, on: Boolean) {
        _state.update { s -> s.copy(feeds = s.feeds.map { if (it.id == id) it.copy(priority = on, ts = now()) else it }) }
        persist()
    }

    /** Rename and/or move a feed to a folder (blank folder = no folder). */
    fun editFeed(id: String, name: String, folder: String, priority: Boolean) {
        _state.update { s ->
            val feeds = s.feeds.map {
                if (it.id == id) it.copy(customTitle = name.trim().takeIf { n -> n != it.title } ?: "", folder = folder.trim(), priority = priority, ts = now()) else it
            }
            val view = s.view
            s.copy(feeds = feeds, view = if (view is ReaderView.Folder && feeds.none { it.folder == view.name }) ReaderView.Today else view)
        }
        persist()
    }

    fun removeFeed(id: String) {
        val title = _state.value.feeds.firstOrNull { it.id == id }?.name
        _state.update { s ->
            s.copy(
                feeds = s.feeds.filter { it.id != id },
                deletedFeeds = s.deletedFeeds + (id to now()),
                articles = s.articles.filter { it.feedId != id },
                view = s.view.let { v ->
                    val remaining = s.feeds.filter { it.id != id }
                    if (v == ReaderView.OneFeed(id) || (v is ReaderView.Folder && remaining.none { it.folder == v.name })) ReaderView.Today else v
                },
            )
        }
        persist()
        if (title != null) _messages.tryEmit("Unsubscribed from $title")
    }

    fun refreshIfStale() {
        syncSoon(500)
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
            persist() // also schedules a sync, which applies synced state to the articles just fetched
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
        val fresh = entries.distinctBy { it.url }.map { Feed(stableId(it.url), it.url, it.title.ifBlank { hostOf(it.url) }, folder = it.folder, ts = now()) }
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
                store.save(s.toSnapshot())
            }
        }
        syncSoon(3000)
    }

    private fun now() = System.currentTimeMillis()

    // ---- Sync -------------------------------------------------------------

    /** Sync a moment from now; further changes inside the delay fold into the same run. */
    private fun syncSoon(delayMs: Long) {
        if (cloud.session() == null) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    fun syncNow() {
        if (cloud.session() == null) return
        viewModelScope.launch(Dispatchers.IO) {
            syncLock.withLock {
                _state.update { it.copy(syncStatus = SyncStatus.Syncing, syncError = null) }
                try {
                    var attempts = 0
                    while (true) {
                        val remote = cloud.pull()
                        val before = _state.value.feeds.map { it.id }.toHashSet()
                        var json: String? = null
                        var needsPush = false
                        _state.update { s ->
                            val r = SyncMerge.merge(s.toSnapshot(), remote.json, now())
                            json = r.json; needsPush = r.needsPush
                            if (r.changedLocal) s.withSnapshot(r.snapshot) else s
                        }
                        val ok = !needsPush || cloud.push(json!!, remote.updateTime)
                        if (ok) {
                            val added = _state.value.feeds.any { it.id !in before }
                            _state.update { it.copy(syncStatus = SyncStatus.Synced, lastSync = now()) }
                            persistNow()
                            if (added) refreshAll(quiet = true)
                            break
                        }
                        if (++attempts >= 3) throw SyncException("Another device kept changing the data. Try again in a moment.")
                    }
                } catch (e: Exception) {
                    val msg = if (e is UnknownHostException || e is SocketTimeoutException) "Can't reach the sync service. Are you offline?" else e.message ?: "Sync failed"
                    _state.update { it.copy(syncStatus = SyncStatus.Error, syncError = msg) }
                }
            }
        }
    }

    private suspend fun persistNow() = saveLock.withLock { store.save(_state.value.toSnapshot()) }

    /** Called with the Google ID token from the sign-in sheet. */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(syncStatus = SyncStatus.Syncing, syncError = null) }
            try {
                val session = withContext(Dispatchers.IO) { cloud.signIn(idToken) }
                _state.update { it.copy(syncEmail = session.email) }
                syncNow()
            } catch (e: Exception) {
                _state.update { it.copy(syncStatus = SyncStatus.Error, syncError = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun signInFailed(message: String) = _state.update { it.copy(syncStatus = SyncStatus.Error, syncError = message) }

    fun signOut() {
        syncJob?.cancel()
        cloud.signOut()
        _state.update { it.copy(syncEmail = null, syncStatus = SyncStatus.Off, syncError = null, lastSync = 0) }
        _messages.tryEmit("Signed out. Your feeds stay on this device.")
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
