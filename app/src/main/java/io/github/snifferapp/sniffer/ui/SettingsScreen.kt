package io.github.snifferapp.sniffer.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.snifferapp.sniffer.settings.Settings
import io.github.snifferapp.sniffer.settings.SettingsStore
import kotlinx.coroutines.launch

const val ATTRIBUTION = "Tower data © OpenCelliD contributors, CC BY-SA 4.0"

@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
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

        Text("OpenCelliD API key", style = MaterialTheme.typography.titleMedium)
        Text("Tower positions come from OpenCelliD. Create a free account to get your own key. It's stored only on this phone.")
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
        Text("Sniffer is free software, licensed under the GNU GPL v3 or later. Sound engine: libpd (Pure Data).", style = MaterialTheme.typography.bodySmall)
    }
}
