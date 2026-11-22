package io.github.snifferapp.sniffer.engine

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.live.LiveCell
import io.github.snifferapp.sniffer.live.LiveSnapshot
import io.github.snifferapp.sniffer.settings.Settings
import io.github.snifferapp.sniffer.sound.SoundControls
import io.github.snifferapp.sniffer.sound.SoundSink
import io.github.snifferapp.sniffer.towers.DbStatus
import io.github.snifferapp.sniffer.towers.Radio
import io.github.snifferapp.sniffer.towers.TowerSite
import io.github.snifferapp.sniffer.towers.TowersResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSink : SoundSink {
    var running = false
    val sent = mutableListOf<SoundControls>()
    override fun start() { running = true }
    override fun stop() { running = false }
    override fun send(c: SoundControls) { sent += c }
}

private fun lte(ci: Long) = LiveCell(Radio.LTE, 238, 1, 100, ci, null, -90, true)

@OptIn(ExperimentalCoroutinesApi::class)
class SnifferEngineTest {
    private val start = LatLon(55.0, 12.0)
    private val location = MutableStateFlow<LatLon?>(start)
    private val live = MutableStateFlow<LiveSnapshot?>(LiveSnapshot(lte(1), emptyList(), 4, 0))
    private val settings = MutableStateFlow(Settings(apiKey = "k"))
    private val sink = FakeSink()
    private val queries = mutableListOf<LatLon>()

    private fun TestScope.engine() = SnifferEngine(
        scope = backgroundScope,
        location = object : LocationSource { override fun locations(intervalMs: Long): Flow<LatLon?> = location },
        towers = { c, _, _ -> queries += c; TowersResult(listOf(TowerSite("s", 238, 1, c, emptyList())), DbStatus.OK, true) },
        live = { live.filterNotNull() },
        sink = sink,
        settings = settings,
        nowMs = { testScheduler.currentTime },
    )

    @Test fun engine_off_sinkStopped() = runTest {
        val e = engine()
        e.setMode(Mode.AMBIENT); runCurrent()
        assertTrue(sink.running)
        e.setMode(Mode.OFF); runCurrent()
        assertFalse(sink.running)
    }

    @Test fun engine_ambient_sendsControlsEverySecond() = runTest {
        engine().setMode(Mode.AMBIENT)
        advanceTimeBy(5_500)
        assertTrue("sent ${sink.sent.size}", sink.sent.size in 5..6)
    }

    @Test fun engine_noLocation_stillEmitsControls() = runTest {
        location.value = null
        val e = engine()
        e.setMode(Mode.AMBIENT)
        advanceTimeBy(2_500)
        assertTrue(sink.sent.isNotEmpty())
        assertEquals(0, queries.size)
        assertNotNull(e.state.value.controls)
    }

    @Test fun engine_moved350m_requeriesTowers() = runTest {
        engine().setMode(Mode.AMBIENT)
        advanceTimeBy(2_000)
        assertEquals(1, queries.size)
        location.value = LatLon(55.0 + 350 / 111_195.0, 12.0)
        advanceTimeBy(2_000)
        assertEquals(2, queries.size)
    }

    @Test fun engine_moved100m_noRequery() = runTest {
        engine().setMode(Mode.AMBIENT)
        advanceTimeBy(2_000)
        location.value = LatLon(55.0 + 100 / 111_195.0, 12.0)
        advanceTimeBy(5_000)
        assertEquals(1, queries.size)
    }

    @Test fun engine_requeriesAfter60s() = runTest {
        engine().setMode(Mode.AMBIENT)
        advanceTimeBy(61_500)
        assertEquals(2, queries.size)
    }

    @Test fun engine_arMuted_masterZero() = runTest {
        settings.value = Settings(apiKey = "k", arMuted = true)
        engine().setMode(Mode.AR)
        advanceTimeBy(1_500)
        assertEquals(0f, sink.sent.last().master, 0f)
    }

    @Test fun engine_ambientVolume_usedInAmbient() = runTest {
        settings.value = Settings(apiKey = "k", ambientVolume = 0.6f, arMuted = true)
        engine().setMode(Mode.AR_AND_AMBIENT)
        advanceTimeBy(1_500)
        assertEquals(0.6f, sink.sent.last().master, 0f)
    }

    @Test fun engine_handover_sentOnce() = runTest {
        engine().setMode(Mode.AMBIENT)
        advanceTimeBy(3_000)
        live.value = LiveSnapshot(lte(2), emptyList(), 4, 20_000)
        advanceTimeBy(5_000)
        assertEquals(1, sink.sent.count { it.handover })
    }

    @Test fun engine_state_hasSitesAndServingMatch() = runTest {
        val e = engine()
        e.setMode(Mode.AR)
        advanceTimeBy(2_000)
        assertEquals(1, e.state.value.sites.size)
        assertEquals(238 to 1, e.state.value.ownMccMnc)
        assertEquals(DbStatus.OK, e.state.value.dbStatus)
    }
}
