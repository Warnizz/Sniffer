package io.github.warnizz.sniffer.sound

import android.content.Context
import android.util.Log
import org.puredata.android.io.AudioParameters
import org.puredata.android.io.PdAudio
import org.puredata.core.PdBase
import java.io.File

/** libpd-backed [SoundSink]. Plays assets/sniffer.pd. */
class PdSoundSink(private val context: Context) : SoundSink {
    private var patchHandle: Int? = null

    @Synchronized
    override fun start() {
        if (PdAudio.isRunning()) return
        try {
            if (patchHandle == null) {
                PdAudio.initAudio(AudioParameters.suggestSampleRate(), 0, 2, 8, true)
                val patch = File(context.filesDir, PATCH)
                context.assets.open(PATCH).use { input -> patch.outputStream().use { input.copyTo(it) } }
                patchHandle = PdBase.openPatch(patch)
            }
            PdAudio.startAudio(context)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start Pd audio", e)
        }
    }

    @Synchronized
    override fun stop() {
        if (PdAudio.isRunning()) PdAudio.stopAudio()
    }

    override fun send(c: SoundControls) {
        for (m in c.toPdMessages()) when (m) {
            is PdMessage.Float -> PdBase.sendFloat(m.receiver, m.value)
            is PdMessage.Bang -> PdBase.sendBang(m.receiver)
        }
    }

    private companion object {
        const val TAG = "PdSoundSink"
        const val PATCH = "sniffer.pd"
    }
}
