package io.github.akrishna87.songgrab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import io.github.akrishna87.songgrab.ui.SongGrabApp
import io.github.akrishna87.songgrab.ui.SongGrabTheme

class MainActivity : ComponentActivity() {

    private val model: SongGrabViewModel by viewModels()

    /** A save that waits on the permission prompt. */
    private var pendingSave: Pair<String, Format>? = null

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val save = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, "SongGrab needs storage access to save songs in your Music folder.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        // Without the notification permission the download still runs; it just isn't shown.
        DownloadService.add(this, save.first, save.second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark, so keep the status and navigation bar icons light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            SongGrabTheme {
                SongGrabApp(model, onSave = ::save)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        model.player.connect(this)
    }

    override fun onStop() {
        model.player.disconnect()
        super.onStop()
    }

    /** A link shared from YouTube (or opened with SongGrab) starts saving straight away. */
    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Links.find(text)
        if (url == null) {
            Toast.makeText(this, "There's no link in what was shared.", Toast.LENGTH_LONG).show()
            return
        }
        save(url, model.format.value)
    }

    private fun save(url: String, format: Format) {
        val needed = buildList {
            val notifications = Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(notifications) && model.firstAsk(notifications)) add(notifications)
            val storage = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !granted(storage)) add(storage)
        }
        if (needed.isEmpty()) {
            DownloadService.add(this, url, format)
            return
        }
        pendingSave = url to format
        askPermissions.launch(needed.toTypedArray())
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
