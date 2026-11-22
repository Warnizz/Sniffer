package io.github.snifferapp.sniffer.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.snifferapp.sniffer.SnifferApp
import io.github.snifferapp.sniffer.engine.Mode
import io.github.snifferapp.sniffer.engine.SnifferService

class MainActivity : ComponentActivity() {
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        val engine = (application as SnifferApp).engine
        setContent {
            MaterialTheme {
                val state by engine.state.collectAsStateWithLifecycle()
                Column(Modifier.safeDrawingPadding()) {
                    Text("Sniffer — temporary debug screen (replaced in Task 11)")
                    Button(onClick = {
                        if (state.mode == Mode.OFF) SnifferService.start(this@MainActivity) else SnifferService.stop(this@MainActivity)
                    }) { Text(if (state.mode == Mode.OFF) "Start ambient" else "Stop ambient") }
                    Text("mode=${state.mode} user=${state.user} db=${state.dbStatus} sites=${state.sites.size}")
                    Text("serving=${state.live?.serving}")
                    Text("controls=${state.controls}")
                }
            }
        }
    }
}
