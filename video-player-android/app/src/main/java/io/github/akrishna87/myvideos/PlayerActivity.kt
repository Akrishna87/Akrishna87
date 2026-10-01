package io.github.akrishna87.myvideos

import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.akrishna87.myvideos.ui.GestureHint
import io.github.akrishna87.myvideos.ui.MyVideosTheme
import io.github.akrishna87.myvideos.ui.PlayerActions
import io.github.akrishna87.myvideos.ui.PlayerOverlay
import io.github.akrishna87.myvideos.ui.PlayerUiState
import kotlin.math.roundToInt

/**
 * Full-screen video player: Media3 ExoPlayer with its standard controls (play/pause, seek bar,
 * previous/next, speed, audio and subtitle tracks), plus gestures, picture-in-picture, rotation,
 * subtitle files, and picking up where each video was left.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_START = "start"
        private const val EXTRA_FROM_START = "fromStart"
        private const val SEEK_MS = 10_000L
        /** Keeps the intent well under Android's size limit even for huge libraries. */
        private const val MAX_QUEUE = 300

        /** Plays [queue] starting at [startIndex]; the following videos play next, like a playlist. */
        fun start(context: Context, queue: List<Video>, startIndex: Int, fromStart: Boolean = false) {
            if (queue.isEmpty()) return
            val index = startIndex.coerceIn(0, queue.lastIndex)
            val from = (index - MAX_QUEUE / 3).coerceAtLeast(0)
            val window = queue.subList(from, minOf(queue.size, from + MAX_QUEUE))
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_IDS, window.map { it.id }.toLongArray())
                    .putExtra(EXTRA_TITLES, window.map { it.title }.toTypedArray())
                    .putExtra(EXTRA_START, index - from)
                    .putExtra(EXTRA_FROM_START, fromStart),
            )
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var playerView: PlayerView
    private lateinit var gestures: GestureLayout
    private lateinit var progressStore: WatchProgress
    private lateinit var audio: AudioManager

    private val ui = PlayerUiState()
    private val handler = Handler(Looper.getMainLooper())

    /** The person chose an orientation with the rotate button, so stop following the video's shape. */
    private var userOrientation = false
    private var videoAspect = Rational(16, 9)
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    // The video being watched, and how far in, for saving progress.
    private var lastKey: String? = null
    private var lastPos = 0L
    private var lastDur = 0L
    private var warnedAudioFor: String? = null

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitles(uri)
    }

    private val clearHint = Runnable { ui.hint = null }
    private val clearResumed = Runnable { ui.resumedAt = null }
    private val ticker = object : Runnable {
        var ticks = 0
        override fun run() {
            if (++ticks % 5 == 0) saveProgress() else readPosition()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressStore = WatchProgress(this)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
        player.addListener(listener)
        // Lets headphone buttons and the system's media controls drive the player.
        session = MediaSession.Builder(this, player).build()

        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            setShowSubtitleButton(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 3500
            setBackgroundColor(android.graphics.Color.BLACK)
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility -> ui.controlsVisible = visibility == View.VISIBLE },
            )
        }
        gestures = GestureLayout(this, gestureCallbacks).apply {
            addView(playerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val actions = PlayerActions(
            onBack = { finish() },
            onSubtitles = { subtitlePicker.launch(arrayOf("*/*")) },
            onResize = ::cycleResizeMode,
            onRotate = ::rotate,
            onPip = if (supportsPip()) ::enterPip else null,
            onStartOver = {
                player.seekTo(0)
                ui.resumedAt = null
            },
        )
        setContent {
            MyVideosTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(factory = { gestures }, modifier = Modifier.fillMaxSize())
                    PlayerOverlay(ui, actions)
                }
            }
        }

        load(intent)
        handler.post(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress()
        lastKey = null
        load(intent)
    }

    private fun load(intent: Intent) {
        val ids = intent.getLongArrayExtra(EXTRA_IDS)
        val items: List<MediaItem>
        val start: Int
        if (ids != null) {
            val titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: emptyArray()
            items = ids.mapIndexed { i, id ->
                mediaItem(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), id.toString(), titles.getOrNull(i) ?: "Video")
            }
            start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        } else {
            // "Open with My Videos" from another app.
            val uri = intent.data
            items = if (uri == null) emptyList() else listOf(mediaItem(uri, Video.keyFor(uri), displayName(uri).substringBeforeLast('.')))
            start = 0
        }
        if (items.isEmpty()) {
            finish()
            return
        }
        val key = items[start].mediaId
        val resume = if (intent.getBooleanExtra(EXTRA_FROM_START, false)) 0L else progressStore.get(key)?.resumeAt ?: 0L
        userOrientation = false
        lastKey = key
        lastPos = resume
        lastDur = 0L
        ui.title = items[start].mediaMetadata.title?.toString().orEmpty()
        player.setMediaItems(items, start, resume)
        player.prepare()
        player.play()
        showResumed(resume)
    }

    private fun mediaItem(uri: Uri, key: String, title: String): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
            .build()

    /** The file name another app's URI points at, e.g. "Holiday.mp4". */
    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
            }
        } catch (e: Exception) {
            // Not every app's URI answers queries; fall back to the path.
        }
        return Uri.decode(uri.lastPathSegment ?: "Video").substringAfterLast('/')
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            val key = mediaItem.mediaId
            val previous = lastKey
            if (previous != null && previous != key) {
                // Moving on to the next video: remember how far the last one got.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) progressStore.markWatched(previous, lastDur)
                else if (lastDur > 0) progressStore.save(previous, lastPos, lastDur)
            }
            ui.title = mediaItem.mediaMetadata.title?.toString().orEmpty()
            if (previous == key) return
            lastKey = key
            lastPos = 0L
            lastDur = 0L
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                val resume = progressStore.get(key)?.resumeAt ?: 0L
                if (resume > 0) player.seekTo(resume)
                showResumed(resume)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // The last video in the list finished: back to the library.
                lastKey?.let { progressStore.markWatched(it, if (player.duration > 0) player.duration else lastDur) }
                lastKey = null
                finish()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerView.keepScreenOn = isPlaying
            updatePipParams()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            var w = videoSize.width
            var h = videoSize.height
            if (w <= 0 || h <= 0) return
            if (videoSize.unappliedRotationDegrees % 180 == 90) w = h.also { h = w }
            videoAspect = Rational(w, h)
            if (!userOrientation && !isInPictureInPictureMode) {
                requestedOrientation =
                    if (w >= h) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            updatePipParams()
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Movies often carry DTS or Dolby sound, which many phones can't decode. Say so instead of
            // leaving people wondering why there's no sound.
            val key = lastKey ?: return
            if (key == warnedAudioFor || !tracks.containsType(C.TRACK_TYPE_AUDIO) || tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) return
            warnedAudioFor = key
            val mime = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)?.sampleMimeType.orEmpty()
            toast("This phone can't play this video's ${audioName(mime)} sound, so it will play silently.")
        }

        override fun onPlayerError(error: PlaybackException) {
            val why = when (error.errorCode) {
                in 4000..4999 -> "its video format isn't supported on this phone"
                in 3000..3999 -> "the file looks damaged or incomplete"
                in 2000..2999 -> "the file can't be opened"
                else -> "something went wrong"
            }
            toast("Can't play “${ui.title}”: $why.")
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                lastKey = null
                finish()
            }
        }
    }

    private fun audioName(mime: String): String = when {
        mime.startsWith("audio/vnd.dts") -> "DTS"
        mime == MimeTypes.AUDIO_E_AC3 || mime == MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
        mime == MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC3)"
        mime == MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        mime.isNotEmpty() -> mime.removePrefix("audio/").uppercase()
        else -> "audio"
    }

    // ----- Progress -----

    private fun readPosition() {
        val key = lastKey ?: return
        if (player.currentMediaItem?.mediaId == key && player.duration > 0) {
            lastPos = player.currentPosition
            lastDur = player.duration
        }
    }

    private fun saveProgress() {
        val key = lastKey ?: return
        readPosition()
        if (lastDur > 0) progressStore.save(key, lastPos, lastDur)
    }

    private fun showResumed(at: Long) {
        handler.removeCallbacks(clearResumed)
        ui.resumedAt = at.takeIf { it > 0 }
        if (at > 0) handler.postDelayed(clearResumed, 6000)
    }

    // ----- Top bar buttons -----

    private fun cycleResizeMode() {
        val (mode, label) = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop to fill"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit to screen"
        }
        resizeMode = mode
        playerView.resizeMode = mode
        hint(Icons.Rounded.AspectRatio, label)
    }

    private fun rotate() {
        userOrientation = true
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun addSubtitles(uri: Uri) {
        val name = displayName(uri)
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> {
                toast("That isn't a subtitle file. Pick an .srt, .vtt, .ass or .ttml file.")
                return
            }
        }
        val current = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val subtitles = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLabel(name.substringBeforeLast('.'))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val updated = current.buildUpon().setSubtitleConfigurations(listOf(subtitles)).build()
        val items = (0 until player.mediaItemCount).map { if (it == index) updated else player.getMediaItemAt(it) }
        player.setMediaItems(items, index, position)
        player.prepare()
        // Make sure subtitles are on, in case they were switched off in the player's menu.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        toast("Subtitles added")
    }

    // ----- Gestures -----

    private val gestureCallbacks = object : GestureLayout.Callbacks {
        private var startLevel = 0f
        private val maxVolume get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        override fun onDoubleTapSide(forward: Boolean) {
            if (forward) player.seekForward() else player.seekBack()
            if (forward) hint(Icons.Rounded.FastForward, "+10 s") else hint(Icons.Rounded.FastRewind, "−10 s")
        }

        override fun onVerticalDragStart(leftSide: Boolean) {
            startLevel = if (leftSide) currentBrightness() else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        }

        override fun onVerticalDrag(leftSide: Boolean, fraction: Float) {
            val level = (startLevel + fraction * 1.2f).coerceIn(0f, 1f)
            handler.removeCallbacks(clearHint)
            if (leftSide) {
                window.attributes = window.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) }
                ui.hint = GestureHint(Icons.Rounded.BrightnessMedium, "Brightness ${(level * 100).roundToInt()}%", level)
            } else {
                val steps = (level * maxVolume).roundToInt()
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
                } catch (e: SecurityException) {
                    // Do Not Disturb can block volume changes.
                }
                ui.hint = GestureHint(
                    if (steps == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    "Volume $steps",
                    steps.toFloat() / maxVolume,
                )
            }
        }

        override fun onDragEnd() = hideHintSoon()
    }

    /** This window's brightness, or the phone's own setting until the person changes it here. */
    private fun currentBrightness(): Float {
        val own = window.attributes.screenBrightness
        if (own >= 0) return own
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun hint(icon: ImageVector, text: String) {
        ui.hint = GestureHint(icon, text)
        hideHintSoon()
    }

    private fun hideHintSoon() {
        handler.removeCallbacks(clearHint)
        handler.postDelayed(clearHint, 700)
    }

    // ----- Picture-in-picture -----

    private fun supportsPip() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        // Android only allows shapes between 1:2.39 and 2.39:1.
        val ratio = (videoAspect.toFloat()).coerceIn(1 / 2.39f + 0.001f, 2.39f - 0.001f)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .apply { if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(player.isPlaying) }
            .build()
    }

    private fun updatePipParams() {
        if (!supportsPip()) return
        try {
            setPictureInPictureParams(pipParams())
        } catch (e: Exception) {
            // Some phones report picture-in-picture but refuse it.
        }
    }

    private fun enterPip() {
        if (!supportsPip()) return
        try {
            enterPictureInPictureMode(pipParams())
        } catch (e: Exception) {
            toast("Picture-in-picture isn't available")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ does this by itself (setAutoEnterEnabled).
        if (Build.VERSION.SDK_INT < 31 && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        gestures.gesturesEnabled = !isInPictureInPictureMode
        playerView.useController = !isInPictureInPictureMode
        if (!isInPictureInPictureMode) hideSystemBars()
    }

    // ----- Lifecycle -----

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isInPictureInPictureMode) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player.pause()
        // The picture-in-picture window was closed: don't leave a stopped player behind.
        if (isInPictureInPictureMode) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
