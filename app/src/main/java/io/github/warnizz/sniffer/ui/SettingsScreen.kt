package io.github.warnizz.sniffer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.warnizz.sniffer.settings.Settings
import io.github.warnizz.sniffer.settings.SettingsStore
import io.github.warnizz.sniffer.towers.CountryDataManager
import kotlinx.coroutines.launch

const val ATTRIBUTION = "Cell tower data from OpenCellID · CC BY-SA 4.0. Changes: unused columns removed."

@Composable
fun SettingsScreen(store: SettingsStore, countryData: CountryDataManager, onDownload: (Int) -> Unit, onRemove: (Int) -> Unit, onCheckUpdates: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(Settings())
    var keyField by remember { mutableStateOf("") }
    LaunchedEffect(settings.apiKey) { keyField = settings.apiKey.orEmpty() }
    fun save(f: (Settings) -> Settings) = scope.launch { store.update(f) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        val countries by countryData.state.collectAsStateWithLifecycle()
        var mccField by remember { mutableStateOf("") }
        Text("Tower data", style = MaterialTheme.typography.titleMedium)
        Text("Countries are downloaded once from the project's mirror and work offline. Countries are identified by their mobile country code (MCC), e.g. 238 = Denmark.")
        if (countries.records.isEmpty()) Text("No countries downloaded yet.", style = MaterialTheme.typography.bodySmall)
        countries.records.forEach { rec ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MCC ${rec.mcc} · ${rec.rows} cells · ${rec.generated}", Modifier.weight(1f))
                TextButton(onClick = { onRemove(rec.mcc) }) { Text("Remove") }
            }
        }
        countries.busyMcc?.let { Text("Downloading MCC $it… ${countries.progressRows} cells") }
        countries.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = mccField, onValueChange = { mccField = it.filter(Char::isDigit).take(3) }, singleLine = true,
                label = { Text("Add MCC") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(enabled = mccField.length == 3 && countries.busyMcc == null, onClick = { onDownload(mccField.toInt()); mccField = "" }) {
                Text("Download")
            }
        }
        OutlinedButton(enabled = countries.busyMcc == null, onClick = onCheckUpdates) { Text("Check for updates") }

        Text("OpenCelliD API key (optional)", style = MaterialTheme.typography.titleMedium)
        Text("Not needed. With your own free OpenCelliD key, Sniffer also fetches fresh tower data live for the area around you. The key is stored only on this phone.")
        OutlinedTextField(
            value = keyField, onValueChange = { keyField = it }, singleLine = true, label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { save { it.copy(apiKey = keyField.trim().ifEmpty { null }) } }) { Text("Save key") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://opencellid.org/"))) }) {
                Text("Get a key")
            }
        }

        Text("Search radius", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1000, 2000, 3000).forEach { r ->
                FilterChip(selected = settings.radiusM == r, onClick = { save { it.copy(radiusM = r) } }, label = { Text("${r / 1000} km") })
            }
        }

        Text("Ambient volume", style = MaterialTheme.typography.titleMedium)
        Slider(value = settings.ambientVolume, onValueChange = { v -> save { it.copy(ambientVolume = v) } })
        Text("Look-around volume", style = MaterialTheme.typography.titleMedium)
        Slider(value = settings.arVolume, onValueChange = { v -> save { it.copy(arVolume = v) } })

        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(ATTRIBUTION, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://opencellid.org"))) }) { Text("OpenCellID") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by-sa/4.0/"))) }) { Text("CC BY-SA 4.0") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Warnizz/Sniffer"))) }) { Text("Source code") }
        }
        Text("Sniffer is free software, licensed under the GNU GPL v3 or later. Sound engine: libpd (Pure Data).", style = MaterialTheme.typography.bodySmall)
    }
}
