package io.github.akrishna87.mytorrents.engine

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.InfoHash
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t

/** One torrent as the screens show it. */
data class Torrent(
    /** The info-hash in hex; stays the same for the life of the torrent. */
    val id: String,
    val name: String,
    val status: Status,
    /** 0..1 of the files that are wanted. */
    val progress: Float,
    val wantedBytes: Long,
    val doneBytes: Long,
    val downloadRate: Int,
    val uploadRate: Int,
    val uploadedBytes: Long,
    val peers: Int,
    val seeds: Int,
    val savePath: String,
    val hasMetadata: Boolean,
    val error: String?,
    /** Seconds since 1970. */
    val addedAt: Long,
) {
    /** Seconds left at the current speed, or -1 if it can't be guessed. */
    val etaSeconds: Long
        get() = if (status == Status.Downloading && downloadRate > 0) (wantedBytes - doneBytes) / downloadRate else -1
}

enum class Status { GettingInfo, Checking, Queued, Downloading, Seeding, Finished, Paused, Error }

/** One file inside a torrent. [path] is relative to the torrent's save folder. */
data class TorrentFile(val index: Int, val path: String, val size: Long, val done: Long, val wanted: Boolean)

/** Session-wide settings the user can change. Speeds are in KB/s; 0 means no limit. */
data class EngineSettings(val downloadLimitKb: Int = 0, val uploadLimitKb: Int = 0)

/**
 * Runs libtorrent and keeps a list of the torrents in it.
 *
 * Every torrent is saved as libtorrent "resume data" in [stateDir] (one `<id>.resume` file each),
 * which holds the magnet/torrent info, the save folder, which pieces are already downloaded,
 * and whether it's paused. [start] adds them all back, so downloads carry on after a restart.
 *
 * This class only uses plain Java/Kotlin, so it can also be tried out off the phone.
 */
class TorrentEngine(private val stateDir: File) {

    interface Events {
        /** A download completed (not called again for torrents that were already complete). */
        fun onFinished(torrent: Torrent) {}

        /** Something the user should be told about, e.g. a torrent that couldn't be added. */
        fun onMessage(message: String) {}
    }

    var events: Events = object : Events {}

    /** If false (the default), a torrent stops sharing as soon as it has finished downloading. */
    @Volatile
    var seedWhenFinished = false

    private val session = SessionManager(false)
    private val handles = ConcurrentHashMap<String, TorrentHandle>()
    private var scope: CoroutineScope? = null

    /** Torrents seen still downloading in this run: only these get a "finished" notification. */
    private val incomplete = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var pendingSaves: CountDownLatch? = null
    private var refreshFailed = false

    private val _torrents = MutableStateFlow<List<Torrent>>(emptyList())

    /** All torrents, newest first. Refreshed every second. */
    val torrents: StateFlow<List<Torrent>> = _torrents

    val isRunning: Boolean get() = session.isRunning

    fun start(settings: EngineSettings) {
        if (session.isRunning) return
        stateDir.mkdirs()
        session.addListener(alerts)
        val params = SessionParams(settingsPack(settings))
        // Plain read/write file access instead of memory-mapped files: phone storage is often a
        // FUSE file system, where memory-mapping big files is slow or fails.
        params.setPosixDiskIO()
        session.start(params)
        restore()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).apply {
            launch {
                var ticks = 0
                while (isActive) {
                    refresh()
                    if (++ticks % 30 == 0) saveChanged()
                    delay(1000)
                }
            }
        }
    }

    /** Saves every torrent's progress (waiting a few seconds for it), then shuts libtorrent down. */
    fun stop() {
        if (!session.isRunning) return
        scope?.cancel()
        scope = null
        val live = handles.values.filter { it.isValid }
        val latch = CountDownLatch(live.size)
        pendingSaves = latch
        live.forEach { it.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
        latch.await(5, TimeUnit.SECONDS)
        pendingSaves = null
        session.removeListener(alerts)
        session.stop()
        handles.clear()
    }

    fun applySettings(settings: EngineSettings) {
        if (session.isRunning) session.applySettings(settingsPack(settings))
    }

    /** Pauses or resumes all network activity, e.g. while not on Wi-Fi. Torrents keep their own state. */
    fun setNetworkAllowed(allowed: Boolean) {
        if (!session.isRunning) return
        if (allowed && session.isPaused) session.resume()
        if (!allowed && !session.isPaused) session.pause()
    }

    /**
     * Adds a magnet link, downloading into [saveDir]. Returns the torrent's id.
     * Throws [IllegalArgumentException] if it isn't a valid magnet link.
     */
    fun addMagnet(uri: String, saveDir: File): String {
        val params = AddTorrentParams.parseMagnetUri(uri.trim())
        val id = idOf(params.infoHashes)
        val existing = handles[id]
        if (existing != null) {
            // Opening the same link again: try the peers it names, in case the old ones went away.
            if (existing.isValid) params.peers().forEach { existing.swig().connect_peer(it.swig()) }
            return id
        }
        if (params.name.isNullOrEmpty()) params.name = id
        incomplete += id
        add(params, saveDir)
        return id
    }

    /**
     * Adds a .torrent file's contents, downloading into [saveDir]. Returns the torrent's id.
     * Throws [IllegalArgumentException] if it isn't a valid torrent file.
     */
    fun addTorrentFile(bytes: ByteArray, saveDir: File): String {
        val info = try {
            TorrentInfo(bytes)
        } catch (e: Throwable) {
            throw IllegalArgumentException("That isn't a valid .torrent file", e)
        }
        val id = idOf(info.infoHashes())
        if (handles.containsKey(id)) return id
        val params = AddTorrentParams()
        params.torrentInfo = info
        incomplete += id
        add(params, saveDir)
        return id
    }

    /** The name inside a .torrent file, without adding it. Throws [IllegalArgumentException] if it isn't one. */
    fun torrentName(bytes: ByteArray): String = try {
        TorrentInfo(bytes).name()
    } catch (e: Throwable) {
        throw IllegalArgumentException("That isn't a valid .torrent file", e)
    }

    private fun add(params: AddTorrentParams, saveDir: File) {
        saveDir.mkdirs()
        params.savePath = saveDir.absolutePath
        // Start right away, and not "auto-managed": libtorrent's queue would otherwise decide when it
        // runs (only every 30 s or so), which makes pause/resume feel broken.
        params.flags = params.flags.and_(TorrentFlags.PAUSED.inv()).and_(TorrentFlags.AUTO_MANAGED.inv())
        session.swig().async_add_torrent(params.swig())
    }

    fun pause(id: String) {
        val h = handles[id]?.takeIf { it.isValid } ?: return
        h.unsetFlags(TorrentFlags.AUTO_MANAGED)
        h.pause()
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        refresh()
    }

    fun resume(id: String) {
        val h = handles[id]?.takeIf { it.isValid } ?: return
        h.swig().clear_error()
        h.unsetFlags(TorrentFlags.AUTO_MANAGED)
        h.resume()
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        refresh()
    }

    fun pauseAll() = handles.keys.forEach(::pause)

    fun resumeAll() = handles.keys.forEach(::resume)

    /** Removes a torrent from the list; with [deleteFiles] its downloaded files are deleted too. */
    fun remove(id: String, deleteFiles: Boolean) {
        val h = handles.remove(id)
        File(stateDir, "$id.resume").delete()
        if (h != null && h.isValid) {
            if (deleteFiles) session.remove(h, SessionHandle.DELETE_FILES) else session.remove(h)
        }
        refresh()
    }

    /** The files in a torrent, or an empty list while its info is still being fetched. */
    fun files(id: String): List<TorrentFile> {
        val h = handles[id]?.takeIf { it.isValid } ?: return emptyList()
        val info = h.torrentFile() ?: return emptyList()
        val storage = info.files()
        val progress = h.fileProgress(TorrentHandle.PIECE_GRANULARITY)
        val priorities = h.filePriorities()
        return (0 until storage.numFiles())
            .filterNot { storage.padFileAt(it) }
            .map { i ->
                TorrentFile(
                    index = i,
                    path = storage.filePath(i),
                    size = storage.fileSize(i),
                    done = progress.getOrElse(i) { 0L },
                    wanted = priorities.getOrNull(i) != Priority.IGNORE,
                )
            }
    }

    /** Chooses whether one file of a torrent gets downloaded. */
    fun setFileWanted(id: String, index: Int, wanted: Boolean) {
        val h = handles[id]?.takeIf { it.isValid } ?: return
        val st = h.status(true)
        h.filePriority(index, if (wanted) Priority.DEFAULT else Priority.IGNORE)
        // A finished torrent that is now missing a file should go and fetch it.
        val missing = wanted && h.fileProgress(TorrentHandle.PIECE_GRANULARITY).getOrElse(index) { 0L } < (h.torrentFile()?.files()?.fileSize(index) ?: 0L)
        if (missing && st.isFinished) {
            if (st.flags().has(TorrentFlags.PAUSED)) resume(id)
            // Finished torrents drop their connections to other complete copies; go and find them again.
            h.forceReannounce()
            h.forceDHTAnnounce()
        }
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        refresh()
    }

    /** The full path of a torrent's file on the phone, or null if unknown. */
    fun filePath(id: String, index: Int): File? {
        val h = handles[id]?.takeIf { it.isValid } ?: return null
        val info = h.torrentFile() ?: return null
        return File(h.savePath(), info.files().filePath(index))
    }

    /** A magnet link for sharing the torrent with someone else. */
    fun magnetLink(id: String): String? = handles[id]?.takeIf { it.isValid }?.makeMagnetUri()

    private fun settingsPack(settings: EngineSettings): SettingsPack = SettingsPack()
        .downloadRateLimit(settings.downloadLimitKb * 1024)
        .uploadRateLimit(settings.uploadLimitKb * 1024)
        // libtorrent waits 60 s before reconnecting to a peer it was connected to, so a paused and
        // resumed torrent would sit at "Looking for peers" for a minute. 10 s is still polite.
        .setInteger(settings_pack.int_types.min_reconnect_time.swigValue(), 10)

    private fun restore() {
        stateDir.listFiles { f -> f.name.endsWith(".resume") }?.forEach { file ->
            try {
                val ec = error_code()
                val params = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(file.readBytes()), ec)
                if (ec.value() != 0) error(ec.message())
                session.swig().async_add_torrent(params)
            } catch (e: Throwable) {
                events.onMessage("Couldn't load a saved torrent (${e.message})")
                file.renameTo(File(file.path + ".bad"))
            }
        }
    }

    private fun saveChanged() {
        handles.values.forEach { h ->
            if (h.isValid && h.needSaveResumeData()) h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        }
    }

    private fun writeResume(id: String, data: ByteArray) {
        if (!handles.containsKey(id)) return // removed in the meantime
        val tmp = File(stateDir, "$id.resume.tmp")
        tmp.writeBytes(data)
        tmp.renameTo(File(stateDir, "$id.resume"))
    }

    @Synchronized
    private fun refresh() {
        if (!session.isRunning) return
        _torrents.value = handles.entries.mapNotNull { (id, h) ->
            if (!h.isValid) return@mapNotNull null
            try {
                toTorrent(id, h.status(TorrentHandle.QUERY_NAME.or_(TorrentHandle.QUERY_SAVE_PATH)))
            } catch (e: Throwable) {
                if (!refreshFailed) {
                    refreshFailed = true
                    events.onMessage("Couldn't read a torrent's status: $e")
                }
                null
            }
        }.sortedByDescending { it.addedAt }
    }

    private fun toTorrent(id: String, st: TorrentStatus): Torrent {
        val flags = st.flags()
        val paused = flags.has(TorrentFlags.PAUSED)
        val queued = paused && flags.has(TorrentFlags.AUTO_MANAGED)
        val error = st.errorCode().takeIf { it.isError }?.message
        if (st.state() == TorrentStatus.State.DOWNLOADING || st.state() == TorrentStatus.State.DOWNLOADING_METADATA) incomplete += id
        val status = when {
            error != null -> Status.Error
            st.state() == TorrentStatus.State.CHECKING_FILES ||
                st.state() == TorrentStatus.State.CHECKING_RESUME_DATA -> Status.Checking
            st.isFinished -> if (paused) Status.Finished else Status.Seeding
            queued -> Status.Queued
            paused -> Status.Paused
            !st.hasMetadata() -> Status.GettingInfo
            else -> Status.Downloading
        }
        return Torrent(
            id = id,
            name = st.name().ifEmpty { id },
            status = status,
            progress = st.progress(),
            wantedBytes = st.totalWanted(),
            doneBytes = st.totalWantedDone(),
            downloadRate = if (paused) 0 else st.downloadPayloadRate(),
            uploadRate = if (paused) 0 else st.uploadPayloadRate(),
            uploadedBytes = st.allTimeUpload(),
            peers = st.numPeers(),
            seeds = st.numSeeds(),
            savePath = st.swig().getSave_path(),
            hasMetadata = st.hasMetadata(),
            error = error,
            addedAt = st.addedTime(),
        )
    }

    private val alerts = object : AlertListener {
        override fun types() = intArrayOf(
            AlertType.ADD_TORRENT.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
            AlertType.SAVE_RESUME_DATA_FAILED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.FILE_ERROR.swig(),
        )

        override fun alert(alert: Alert<*>) {
            try {
                when (alert) {
                    is AddTorrentAlert -> onAdded(alert)
                    is MetadataReceivedAlert -> handles[idOf(alert.handle())]?.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
                    is TorrentFinishedAlert -> handles[idOf(alert.handle())]?.let(::onFinished)
                    is SaveResumeDataAlert -> {
                        writeResume(idOf(alert.handle()), AddTorrentParams.writeResumeDataBuf(alert.params()))
                        pendingSaves?.countDown()
                    }
                    is SaveResumeDataFailedAlert -> pendingSaves?.countDown()
                    is TorrentErrorAlert -> events.onMessage("${alert.handle().name}: ${alert.error().message}")
                    is FileErrorAlert -> events.onMessage("${alert.handle().name}: ${alert.error().message} (${alert.filename()})")
                }
            } catch (e: Throwable) {
                events.onMessage("Torrent engine error: $e")
            }
        }
    }

    private fun onAdded(alert: AddTorrentAlert) {
        if (alert.error().isError) {
            events.onMessage("Couldn't add the torrent: ${alert.error().message}")
            return
        }
        // An alert's handle is only valid until the next batch of alerts, so keep the session's own one.
        val hashes = alert.handle().swig().info_hashes()
        val found = if (hashes.has_v1()) session.swig().find_torrent(hashes.getV1()) else session.swig().find_torrent(hashes.getV2())
        val h = TorrentHandle(found)
        if (!h.isValid) return
        val id = idOf(h)
        handles[id] = h
        // Saved by an older version as "queued" (auto-managed): run it, under the app's own control.
        val flags = h.status(true).flags()
        if (flags.has(TorrentFlags.AUTO_MANAGED)) {
            h.unsetFlags(TorrentFlags.AUTO_MANAGED)
            if (flags.has(TorrentFlags.PAUSED)) h.resume()
        }
        // Write the resume file straight away, so the torrent is remembered even if the app is closed now.
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        refresh()
    }

    private fun onFinished(h: TorrentHandle) {
        val id = idOf(h)
        val st = h.status(TorrentHandle.QUERY_NAME.or_(TorrentHandle.QUERY_SAVE_PATH))
        if (!seedWhenFinished) {
            h.unsetFlags(TorrentFlags.AUTO_MANAGED)
            h.pause()
        }
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        refresh()
        // libtorrent also says "finished" when it loads a torrent that was already complete.
        if (incomplete.remove(id)) events.onFinished(toTorrent(id, st))
    }

    private fun idOf(h: TorrentHandle): String = idOf(InfoHash(h.swig().info_hashes()))

    /**
     * The v1 info-hash when there is one, else the (shortened) v2 one. Not simply "the best" hash:
     * a hybrid v1+v2 torrent added from a v1-only magnet link would change id once its info arrives.
     */
    private fun idOf(hashes: InfoHash): String = (if (hashes.hasV1()) hashes.v1 else hashes.best).toHex()

    private fun torrent_flags_t.has(flag: torrent_flags_t) = and_(flag).non_zero()
}
