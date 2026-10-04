package io.github.akrishna87.songgrab

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screen's link to [PlaybackService]: what's playing, and play/pause/skip. */
class PlayerConnection {

    data class NowPlaying(
        val uri: String? = null,
        val title: String = "",
        val artist: String = "",
        val art: String? = null,
        val isPlaying: Boolean = false,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false,
        val shuffle: Boolean = false,
        val repeat: Int = Player.REPEAT_MODE_OFF,
        val position: Long = 0,
        val duration: Long = 0,
    )

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending?.invoke(c)
            pending = null
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pending = action }
    }

    fun refresh() {
        val c = controller ?: return
        val meta = c.mediaMetadata
        _state.value = NowPlaying(
            uri = c.currentMediaItem?.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            art = meta.artworkUri?.path,
            isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            position = c.currentPosition.coerceAtLeast(0),
            duration = c.duration.coerceAtLeast(0),
        )
    }

    fun play(songs: List<Song>, index: Int) = withController { c ->
        c.shuffleModeEnabled = false
        c.setMediaItems(songs.map(::request), index, 0)
        c.prepare()
        c.play()
    }

    fun shuffle(songs: List<Song>) = withController { c ->
        if (songs.isEmpty()) return@withController
        c.setMediaItems(songs.map(::request), songs.indices.random(), 0)
        c.shuffleModeEnabled = true
        c.prepare()
        c.play()
    }

    fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Takes a deleted song out of the play queue. */
    fun removeSong(song: Song) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == song.uri) c.removeMediaItem(i)
        }
    }

    /** Only the id travels to the service, which fills in the rest (see PlaybackService). */
    private fun request(song: Song) = MediaItem.Builder().setMediaId(song.uri).build()
}
