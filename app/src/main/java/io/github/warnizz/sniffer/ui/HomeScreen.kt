package io.github.warnizz.sniffer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.warnizz.sniffer.ar.label
import io.github.warnizz.sniffer.engine.Mode
import io.github.warnizz.sniffer.engine.SnifferEngine
import io.github.warnizz.sniffer.engine.SnifferService
import io.github.warnizz.sniffer.settings.SettingsStore
import io.github.warnizz.sniffer.towers.CountryDataManager
import io.github.warnizz.sniffer.towers.DbStatus

@Composable
fun HomeScreen(engine: SnifferEngine, settingsStore: SettingsStore, countryData: CountryDataManager, onDownload: (Int) -> Unit, onRetryMirror: () -> Unit, onLookAround: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val state by engine.state.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(null)
    var locationGranted by remember { mutableStateOf(context.hasLocation()) }
    var precise by remember { mutableStateOf(context.hasPreciseLocation()) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = context.hasLocation()
        precise = context.hasPreciseLocation()
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        SnifferService.start(context) // runs either way; without the permission the notification is just hidden
    }
    val ambientOn = state.mode == Mode.AMBIENT || state.mode == Mode.AR_AND_AMBIENT

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sniffer", style = MaterialTheme.typography.displaySmall)
        Text("See and hear the cell towers around you.", style = MaterialTheme.typography.bodyLarge)

        if (!locationGranted || !precise) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (locationGranted) "Precise location gives more" else "Location is needed", style = MaterialTheme.typography.titleMedium)
                    Text("Your position is used to find towers near you, and Android only shares live cell details with apps that have precise location. It never leaves your phone except as the map area sent to OpenCelliD.")
                    Button(onClick = { askLocation.launch(LOCATION_PERMISSIONS) }) { Text(if (locationGranted) "Allow precise location" else "Allow location") }
                }
            }
        }

        val countries by countryData.state.collectAsStateWithLifecycle()
        val servingMcc = state.live?.serving?.mcc
        val hasCountry = servingMcc != null && countries.records.any { it.mcc == servingMcc }
        if (countries.busyMcc != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Downloading tower data for MCC ${countries.busyMcc}…", style = MaterialTheme.typography.titleMedium)
                    Text("${countries.progressRows} cells so far")
                }
            }
        } else if (servingMcc != null && !hasCountry) {
            val file = countries.index?.countries?.get(servingMcc)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file != null) {
                        Text("Tower data for your country", style = MaterialTheme.typography.titleMedium)
                        Text("Download the cell towers for MCC $servingMcc (${formatBytes(file.bytes)}) once and Sniffer works offline. No account needed.")
                        Button(onClick = { onDownload(servingMcc) }) { Text("Download ${formatBytes(file.bytes)}") }
                    } else {
                        if (countries.index == null) {
                            Text("Couldn't reach the tower data mirror — check your connection.")
                            OutlinedButton(onClick = onRetryMirror) { Text("Retry") }
                        } else {
                            Text("No tower data for MCC $servingMcc yet. You can add an OpenCelliD key in settings instead.")
                        }
                    }
                    countries.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (state.dbStatus == DbStatus.INVALID_KEY) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OpenCelliD rejected your key — check it in settings")
                    OutlinedButton(onClick = onSettings) { Text("Open settings") }
                }
            }
        }

        Button(onClick = onLookAround, Modifier.fillMaxWidth().height(64.dp)) { Text("Look around", style = MaterialTheme.typography.titleMedium) }
        Button(
            onClick = {
                when {
                    ambientOn -> SnifferService.stop(context)
                    !context.hasNotifications() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> SnifferService.start(context)
                }
            },
            Modifier.fillMaxWidth().height(64.dp),
        ) { Text(if (ambientOn) "Stop ambient" else "Ambient", style = MaterialTheme.typography.titleMedium) }
        Text("Ambient keeps playing with the screen off. Put your headphones on and go for a walk.", style = MaterialTheme.typography.bodySmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Right now", style = MaterialTheme.typography.titleMedium)
                if (state.mode == Mode.OFF) {
                    Text("Start Look around or Ambient to begin listening.")
                } else {
                    val s = state.live?.serving
                    Text(if (s == null) "No serving cell" else "Connected: ${s.radio.label()} · MCC ${s.mcc} / MNC ${s.mnc} · ${s.dbm ?: "?"} dBm")
                    Text("Network type: ${when (state.live?.netType) { 2 -> "2G"; 3 -> "3G"; 4 -> "4G"; 5 -> "5G"; else -> "no service" }}")
                    Text("Neighbour cells heard: ${state.live?.neighbours?.size ?: 0}")
                    Text("Towers nearby: ${state.sites.size}")
                    state.controls?.let { Text("Density: ${(it.density * 100).toInt()} %") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSettings, Modifier.fillMaxWidth()) { Text("Settings") }
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000 -> "%.1f MB".format(b / 1_000_000.0)
    b >= 1_000 -> "${b / 1_000} kB"
    else -> "$b B"
}
