package io.github.snifferapp.sniffer.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundControlsTest {
    private val base = SoundControls(
        density = 0.4f, nearestM = 300f, signal = 0.7f, netType = 4,
        neighbours = 3, handover = false, neighbourEvent = false, master = 0.8f,
    )

    @Test fun toMessages_sendsAllFloats() {
        val floats = base.toPdMessages().filterIsInstance<PdMessage.Float>().associate { it.receiver to it.value }
        assertEquals(
            mapOf("density" to 0.4f, "nearest" to 300f, "signal" to 0.7f, "nettype" to 4f,
                "neighbours" to 3f, "master" to 0.8f),
            floats,
        )
    }

    @Test fun toMessages_noBangsWhenNotFlagged() {
        assertTrue(base.toPdMessages().none { it is PdMessage.Bang })
    }

    @Test fun toMessages_bangsOnlyWhenFlagged() {
        val bangs = base.copy(handover = true, neighbourEvent = true).toPdMessages()
            .filterIsInstance<PdMessage.Bang>().map { it.receiver }
        assertEquals(listOf("handover", "neighbour_event"), bangs)
    }
}
