package io.github.akrishna87.weather.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.weather.WeatherViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    Weather("Weather", Icons.Outlined.WbSunny),
    Wind("Wind map", Icons.Outlined.Air),
    Storms("Storms", Icons.Outlined.Cyclone),
    Sources("Sources", Icons.Outlined.Hub),
}

@Composable
fun WeatherApp(vm: WeatherViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.Weather.ordinal) }
    var handledFocus by rememberSaveable { mutableLongStateOf(0L) }
    val focus by vm.focus.collectAsState()

    // "Show on map" from a storm or the weather screen switches to the map (once per request).
    LaunchedEffect(focus?.seq) {
        val seq = focus?.seq ?: return@LaunchedEffect
        if (seq != handledFocus) {
            handledFocus = seq
            tab = Tab.Wind.ordinal
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refreshIfStale()
        onPauseOrDispose { }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.Weather -> WeatherScreen(vm)
                Tab.Wind -> WindMapScreen(vm)
                Tab.Storms -> StormsScreen(vm)
                Tab.Sources -> SourcesScreen(vm)
            }
        }
    }
}
