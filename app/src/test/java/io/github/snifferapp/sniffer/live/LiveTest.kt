package io.github.snifferapp.sniffer.live

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.towers.Cell
import io.github.snifferapp.sniffer.towers.Radio
import io.github.snifferapp.sniffer.towers.TowerSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lte(ci: Long?, serving: Boolean = true, pci: Int? = null, dbm: Int? = -90) =
    LiveCell(Radio.LTE, 238, 1, 100, ci, pci, dbm, serving)

private fun snap(serving: LiveCell?, neighbours: List<LiveCell> = emptyList(), t: Long = 0) =
    LiveSnapshot(serving, neighbours, netTypeOf(serving?.radio), t)

class SignalNormalizerTest {
    @Test fun normalize_lte_bounds() {
        assertEquals(0f, SignalNormalizer.normalize(Radio.LTE, -140), 1e-4f)
        assertEquals(1f, SignalNormalizer.normalize(Radio.LTE, -44), 1e-4f)
        assertEquals(0.5f, SignalNormalizer.normalize(Radio.LTE, -92), 1e-4f)
    }
    @Test fun normalize_gsm_bounds() {
        assertEquals(0f, SignalNormalizer.normalize(Radio.GSM, -113), 1e-4f)
        assertEquals(1f, SignalNormalizer.normalize(Radio.GSM, -51), 1e-4f)
    }
    @Test fun normalize_umts_mid() = assertEquals(0.5f, SignalNormalizer.normalize(Radio.UMTS, -72), 1e-4f)
    @Test fun normalize_null_isNeutral() = assertEquals(0.5f, SignalNormalizer.normalize(Radio.LTE, null), 0f)
    @Test fun normalize_outOfRange_clamped() {
        assertEquals(0f, SignalNormalizer.normalize(Radio.LTE, -200), 0f)
        assertEquals(1f, SignalNormalizer.normalize(Radio.LTE, -10), 0f)
    }
}

class LiveSnapshotTest {
    @Test fun snapshot_empty_isNeutral() {
        val s = LiveSnapshot.EMPTY
        assertNull(s.serving)
        assertEquals(0.5f, s.signal, 0f)
        assertEquals(0, s.netType)
        assertTrue(s.neighbours.isEmpty())
    }
    @Test fun snapshot_signalFromServing() = assertEquals(0.5f, snap(lte(1, dbm = -92)).signal, 1e-4f)
    @Test fun netType_mapping() {
        assertEquals(2, netTypeOf(Radio.GSM)); assertEquals(3, netTypeOf(Radio.UMTS))
        assertEquals(4, netTypeOf(Radio.LTE)); assertEquals(5, netTypeOf(Radio.NR)); assertEquals(0, netTypeOf(null))
    }
}

class HandoverDetectorTest {
    @Test fun handover_firstSnapshot_false() = assertFalse(HandoverDetector().onSnapshot(snap(lte(1))))

    @Test fun handover_change_true() {
        val d = HandoverDetector()
        d.onSnapshot(snap(lte(1), t = 0))
        assertTrue(d.onSnapshot(snap(lte(2), t = 20_000)))
    }

    @Test fun handover_sameCell_false() {
        val d = HandoverDetector()
        d.onSnapshot(snap(lte(1), t = 0))
        assertFalse(d.onSnapshot(snap(lte(1, dbm = -100), t = 20_000)))
    }

    @Test fun handover_pingPong_rateLimited() {
        val d = HandoverDetector()
        d.onSnapshot(snap(lte(1), t = -20_000))
        assertTrue(d.onSnapshot(snap(lte(2), t = 0)))
        assertFalse(d.onSnapshot(snap(lte(1), t = 3_000)))
        assertTrue(d.onSnapshot(snap(lte(2), t = 12_000)))
    }

    @Test fun handover_toNull_false() {
        val d = HandoverDetector()
        d.onSnapshot(snap(lte(1), t = 0))
        assertFalse(d.onSnapshot(snap(null, t = 20_000)))
        assertFalse(d.onSnapshot(snap(lte(1), t = 40_000))) // back to the same cell after a gap is not a handover
    }

    @Test fun newNeighbours_countsOnlyNew() {
        val d = HandoverDetector()
        assertEquals(0, d.newNeighbours(snap(lte(1), listOf(lte(null, false, pci = 10)))))
        assertEquals(1, d.newNeighbours(snap(lte(1), listOf(lte(null, false, pci = 10), lte(null, false, pci = 11)))))
        assertEquals(0, d.newNeighbours(snap(lte(1), listOf(lte(null, false, pci = 11)))))
    }
}

class NeighbourMatcherTest {
    private val user = LatLon(55.0, 12.0)
    private fun site(id: String, lat: Double, ci: Long, unit: Int? = null) =
        TowerSite(id, 238, 1, LatLon(lat, 12.0), listOf(Cell(Radio.LTE, 238, 1, 100, ci, unit, LatLon(lat, 12.0), 500, 1)))

    @Test fun match_exact_notProbable() {
        val m = matchLive(lte(42), listOf(site("a", 55.01, 41), site("b", 55.02, 42)), user)!!
        assertEquals("b", m.site.id); assertFalse(m.probable)
    }

    @Test fun match_pci_nearest_probable() {
        val sites = listOf(site("far", 55.02, 1, unit = 7), site("near", 55.005, 2, unit = 7), site("other", 55.001, 3, unit = 8))
        val m = matchLive(lte(null, false, pci = 7), sites, user)!!
        assertEquals("near", m.site.id); assertTrue(m.probable)
    }

    @Test fun match_pci_over3km_null() = assertNull(matchLive(lte(null, false, pci = 7), listOf(site("x", 55.04, 1, unit = 7)), user))

    @Test fun match_pci_noUser_null() = assertNull(matchLive(lte(null, false, pci = 7), listOf(site("x", 55.001, 1, unit = 7)), null))

    @Test fun match_noIdNoPci_null() = assertNull(matchLive(lte(null, false), listOf(site("x", 55.001, 1)), user))
}
