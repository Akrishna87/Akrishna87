package io.github.akrishna87.screentime.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.screentime.AppTotal
import io.github.akrishna87.screentime.UsageAccess
import io.github.akrishna87.screentime.UsageViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScreenTimeApp(vm: UsageViewModel = viewModel()) {
    // Coming back from Settings (or from any other app): recheck access and pick up new usage.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val selected = vm.selected
    BackHandler(enabled = selected != null) { vm.selected = null }
    when {
        !vm.hasAccess -> AccessScreen()
        selected != null -> AppScreen(vm, selected)
        else -> OverviewScreen(vm)
    }
}

/** Shown until usage access is turned on. */
@Composable
private fun AccessScreen() {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.BarChart, contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text("Allow usage access", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Neram shows how long you use each app, by day, week and month. " +
                    "To do that, Android needs you to turn on Usage access for it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Your usage stays on this phone: Neram has no internet access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = { UsageAccess.openSettings(context) }) { Text("Open Usage access settings") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Turn on the switch for Neram (find it in the list if asked), then come back here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // Android 13+ greys the switch out ("restricted setting") for apps installed from a
            // browser or file, until the user allows it from the app's App info page.
            if (Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(32.dp))
                Text("Switch greyed out?", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android blocks it for apps installed outside the Play Store. Try the switch once, then " +
                        "open Neram's App info, tap ⋮ at the top right, choose “Allow restricted settings” " +
                        "and confirm. The switch then works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { UsageAccess.openAppInfo(context, context.packageName) }) {
                    Text("Open Neram's App info")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewScreen(vm: UsageViewModel) {
    val report by vm.report.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neram", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (vm.syncing) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item { PeriodPicker(vm) }
            item { SpanNavigator(vm, firstDay, Modifier.padding(top = 8.dp)) }
            val r = report ?: return@LazyColumn
            item {
                val average = r.dailyAverage
                TotalHeader(r.total, if (average != null) "Daily average ${formatDuration(average)}" else "Screen time")
            }
            item { BarChart(r.bars, Modifier.padding(vertical = 8.dp), onDayClick = vm::openDay) }
            if (r.apps.isEmpty()) {
                item {
                    Text(
                        if (vm.syncing) "Reading your usage…" else "No app use recorded for this ${vm.period.label.lowercase()}.",
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                item {
                    Text(
                        "Apps",
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val longest = r.apps.first().foregroundMs
                items(r.apps, key = { it.packageName }) { app ->
                    AppRow(app, longest) { vm.selected = app }
                }
            }
            firstDay?.let { first ->
                item {
                    Text(
                        "Tracking since ${first.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}. " +
                            "Android only keeps about a week of detailed history, so Neram saves it every few hours " +
                            "to build up whole weeks and months.",
                        Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One app's time over the chosen day, week or month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(vm: UsageViewModel, app: AppTotal) {
    val report by vm.appReport.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label ?: app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.selected = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item { PeriodPicker(vm) }
            item { SpanNavigator(vm, firstDay, Modifier.padding(top = 8.dp)) }
            val r = report ?: return@LazyColumn
            item {
                val parts = listOfNotNull(
                    opensText(r.opens),
                    r.dailyAverage?.let { "${formatDuration(it)} a day on average" },
                )
                TotalHeader(r.total, parts.joinToString(" · ").ifEmpty { "Not used" })
            }
            r.chartTitle?.let { title ->
                item {
                    Text(
                        title,
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item { BarChart(r.bars, Modifier.padding(vertical = 8.dp), onDayClick = vm::openDay) }
            item {
                OutlinedButton(
                    onClick = { UsageAccess.openAppInfo(context, app.packageName) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("App info") }
            }
        }
    }
}
