package io.github.akrishna87.selavu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.ui.SelavuApp
import io.github.akrishna87.selavu.ui.SelavuTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShortcut(intent)
        setContent {
            SelavuTheme {
                SelavuApp(vm, onExit = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** The launcher's long-press shortcuts open straight on a new expense or income. */
    private fun handleShortcut(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_EXPENSE -> vm.open(Screen.Edit(TxType.EXPENSE))
            ACTION_ADD_INCOME -> vm.open(Screen.Edit(TxType.INCOME))
        }
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "io.github.akrishna87.selavu.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "io.github.akrishna87.selavu.ADD_INCOME"
    }
}
