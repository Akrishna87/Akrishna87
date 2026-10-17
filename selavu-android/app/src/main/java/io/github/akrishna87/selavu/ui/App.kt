package io.github.akrishna87.selavu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen

@Composable
fun SelavuApp(vm: AppViewModel, onExit: () -> Unit) {
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Recurring items that came due while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshRecurring() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler { if (!vm.back()) onExit() }

    val screen = vm.screen
    val tab = vm.stack.first()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (screen == Screen.Home || screen == Screen.Reports || screen == Screen.Settings) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Screen.Home, onClick = { vm.open(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == Screen.Reports, onClick = { vm.open(Screen.Reports) },
                        icon = { Icon(Icons.Default.PieChart, contentDescription = null) }, label = { Text("Reports") },
                    )
                    NavigationBarItem(
                        selected = tab == Screen.Settings, onClick = { vm.open(Screen.Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        val l = ledger
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        when (val s = screen) {
            Screen.Home -> HomeScreen(vm, l, padding)
            Screen.Reports -> ReportsScreen(vm, l, padding)
            Screen.Settings -> SettingsScreen(vm, l, padding)
            Screen.Categories -> CategoriesScreen(vm, l, padding)
            Screen.Repeating -> RepeatingScreen(vm, l, padding)
            is Screen.Edit -> androidx.compose.runtime.key(s) { EditScreen(vm, l, s.type, s.txId, padding) }
            is Screen.CategoryDetail -> CategoryDetailScreen(vm, l, s.categoryId, padding)
        }
    }
}
