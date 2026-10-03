package io.github.akrishna87.daybook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.daybook.ui.DaybookApp
import kotlinx.coroutines.flow.MutableStateFlow

/** What the app was opened to do: from the widget's + button, or a reminder. */
sealed interface LaunchRequest {
    data object QuickAdd : LaunchRequest
    data class OpenTask(val id: String) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        handle(intent)
        setContent { DaybookApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        when {
            intent.getBooleanExtra(EXTRA_QUICK_ADD, false) -> launch.value = LaunchRequest.QuickAdd
            intent.getStringExtra(EXTRA_OPEN_TASK) != null -> launch.value = LaunchRequest.OpenTask(intent.getStringExtra(EXTRA_OPEN_TASK)!!)
        }
    }

    companion object {
        const val EXTRA_QUICK_ADD = "quick_add"
        const val EXTRA_OPEN_TASK = "open_task"

        /** Read (and cleared) by the app once it has acted on it. */
        val launch = MutableStateFlow<LaunchRequest?>(null)
    }
}
