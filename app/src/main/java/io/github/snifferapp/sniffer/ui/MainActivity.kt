package io.github.snifferapp.sniffer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.snifferapp.sniffer.sound.PdSoundSink
import io.github.snifferapp.sniffer.sound.SoundControls

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sink = PdSoundSink(applicationContext)
        setContent {
            MaterialTheme {
                var playing by remember { mutableStateOf(false) }
                Column(Modifier.safeDrawingPadding()) {
                    Text("Sniffer")
                    // Temporary: test tone (removed in Task 11)
                    Button(onClick = {
                        playing = !playing
                        if (playing) {
                            sink.start()
                            sink.send(SoundControls(0f, 0f, 0.5f, 0, 0, false, false, master = 1f))
                        } else sink.stop()
                    }) { Text(if (playing) "Stop tone" else "Play tone") }
                }
            }
        }
    }
}
