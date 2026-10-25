package io.github.akrishna87.rssreader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import io.github.akrishna87.rssreader.ui.ReaderApp
import io.github.akrishna87.rssreader.ui.ReaderTheme

class MainActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { handleShare(intent); handleOpen(intent) }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val mode = state.settings.theme
            val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            // Bar icons follow the app's theme, not just the phone's.
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            ReaderTheme(mode) {
                ReaderApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
        handleOpen(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        AppState.foreground = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        vm.reloadFromDisk()
        vm.refreshIfStale()
    }

    /** A notification or the widget asked for a particular article. */
    private fun handleOpen(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ARTICLE)?.let { vm.openFromOutside(it) }
    }

    companion object {
        const val EXTRA_ARTICLE = "articleId"
    }

    /** "Share → Reader" from a browser, YouTube, etc. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        vm.share(Regex("https?://\\S+").find(text)?.value ?: text.trim())
    }
}
