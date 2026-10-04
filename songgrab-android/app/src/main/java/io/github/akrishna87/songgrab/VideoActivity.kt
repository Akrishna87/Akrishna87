package io.github.akrishna87.songgrab

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Plays a saved video full screen. Starting it pauses any music, as it takes the audio focus. */
@OptIn(UnstableApi::class)
class VideoActivity : ComponentActivity() {

    companion object {
        private const val POSITION = "position"
        private const val PLAYING = "playing"

        fun open(context: Context, song: Song) {
            context.startActivity(
                Intent(context, VideoActivity::class.java)
                    .setData(Uri.parse(song.uri))
                    .putExtra(Intent.EXTRA_TITLE, song.title),
            )
        }
    }

    private lateinit var view: PlayerView
    private var player: ExoPlayer? = null
    private var position = 0L
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getLong(POSITION) ?: 0L
        playWhenReady = savedInstanceState?.getBoolean(PLAYING) ?: true
        title = intent.getStringExtra(Intent.EXTRA_TITLE)
        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            contentDescription = intent.getStringExtra(Intent.EXTRA_TITLE)
        }
        setContentView(view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        val uri = intent.data ?: return finish()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.setMediaItem(MediaItem.fromUri(uri))
                it.seekTo(position)
                it.playWhenReady = playWhenReady
                it.prepare()
                view.player = it
            }
    }

    override fun onStop() {
        player?.let {
            position = it.currentPosition
            playWhenReady = it.playWhenReady
            it.release()
        }
        view.player = null
        player = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(POSITION, player?.currentPosition ?: position)
        outState.putBoolean(PLAYING, player?.playWhenReady ?: playWhenReady)
    }
}
