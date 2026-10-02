package io.github.akrishna87.signal.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.signal.SignalViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    SIGNAL("Signal", Icons.Rounded.SignalCellularAlt),
    SPOTS("Best spot", Icons.Rounded.Place),
    GUIDE("Guide", Icons.AutoMirrored.Rounded.MenuBook),
}

val NEEDED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalApp(vm: SignalViewModel = viewModel()) {
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    // Permissions and the Location switch can change in Settings while the app is away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions() }

    var skipped by rememberSaveable { mutableStateOf(false) }
    if (!skipped && !(permissions.phone && permissions.location)) {
        PermissionScreen(onDone = { vm.refreshPermissions() }, onSkip = { skipped = true })
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alaimaani", fontWeight = FontWeight.SemiBold)
                    Text(
                        "  அலைமானி",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (Tab.entries[tab]) {
                Tab.SIGNAL -> NowScreen(vm, permissions)
                Tab.SPOTS -> SpotScreen(vm)
                Tab.GUIDE -> GuideScreen()
            }
        }
    }
}

/** Explains the two permissions before Android asks for them. */
@Composable
private fun PermissionScreen(onDone: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        val activity = context as? Activity
        // Android stops showing its dialog after two refusals; Settings is then the only way.
        blocked = result.any { (p, ok) -> !ok && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, p) }
        onDone()
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Rounded.SignalCellularAlt, null,
                Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary,
            )
            Text("Alaimaani", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Measures your mobile signal: how strong it is, how clean it is, which tower and band " +
                    "you're on, and where in your home it's best.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Reason(
                Icons.Rounded.PhoneAndroid, "Phone",
                "To read each SIM's signal and whether it's on 4G or 5G. Alaimaani can't make calls or see your calls.",
            )
            Reason(
                Icons.Rounded.CellTower, "Location",
                "Android only shows tower details (band, cell ID, nearby towers) to apps with location " +
                    "permission. Alaimaani never reads or saves where you are, and has no internet access.",
            )
            Reason(
                Icons.Rounded.LocationOn, "Location switch",
                "For tower details, the phone's Location switch also needs to be on.",
            )
            Spacer(Modifier.height(8.dp))
            if (blocked) {
                Text(
                    "Android won't ask again. Open Settings → Permissions to allow them.",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open Settings") }
            } else {
                Button(onClick = { launcher.launch(NEEDED_PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow")
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(if (askedOnce) "Continue with less detail" else "Not now")
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
