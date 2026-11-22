package io.github.warnizz.sniffer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.warnizz.sniffer.SnifferApp
import io.github.warnizz.sniffer.ar.ArScreen
import kotlinx.coroutines.launch

private enum class Screen { HOME, AR, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SnifferApp
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(app.engine, app.settingsStore, app.countryData,
                            onDownload = { mcc -> app.appScope.launch { app.countryData.download(mcc) } }, onLookAround = { screen = Screen.AR }, onSettings = { screen = Screen.SETTINGS })
                        Screen.AR -> ArScreen(app.engine, app.settingsStore, onBack = { screen = Screen.HOME })
                        Screen.SETTINGS -> SettingsScreen(
                            app.settingsStore, app.countryData,
                            onDownload = { mcc -> app.appScope.launch { app.countryData.download(mcc) } },
                            onRemove = { mcc -> app.appScope.launch { app.countryData.remove(mcc) } },
                            onCheckUpdates = { app.appScope.launch { app.countryData.checkUpdatesNow() } },
                            onBack = { screen = Screen.HOME },
                        )
                    }
                }
            }
        }
    }
}
