package io.github.akrishna87.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.lumen.ui.GalleryApp
import io.github.akrishna87.lumen.ui.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars, like an iOS app; bar icons follow light/dark mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                GalleryApp()
            }
        }
    }
}
