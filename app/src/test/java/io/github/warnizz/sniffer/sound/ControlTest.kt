package io.github.warnizz.sniffer.sound

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.live.LiveCell
import io.github.warnizz.sniffer.live.LiveSnapshot
import io.github.warnizz.sniffer.towers.Radio
import io.github.warnizz.sniffer.towers.TowerSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

private val user = LatLon(55.0, 12.0)
private fun siteAt(dLatDeg: Double, id: String = "s$dLatDeg") = TowerSite(id, 238, 1, LatLon(55.0 + dLatDeg, 12.0), emptyList())
private const val M_PER_DEG = 111_195.0

class DensityCalculatorTest {
    @Test fun raw_oneSiteAt0m_is1() = assertEquals(1.0, DensityCalculator.raw(listOf(siteAt(0.0)), user, 2000.0), 1e-9)
    @Test fun raw_siteAt250m_isHalf() = assertEquals(0.5, DensityCalculator.raw(listOf(siteAt(250 / M_PER_DEG)), user, 2000.0), 0.01)
    @Test fun raw_ignoresBeyondRadius() = assertEquals(0.0, DensityCalculator.raw(listOf(siteAt(2100 / M_PER_DEG)), user, 2000.0), 0.0)
    @Test fun normalize_zero_isZero() = assertEquals(0f, DensityCalculator.normalize(0.0), 0f)
    @Test fun normalize_monotonic_andBelow1() {
        val v = listOf(0.5, 2.0, 8.0, 40.0, 400.0).map { DensityCalculator.normalize(it) }
        assertTrue(v.zipWithNext().all { (a, b) -> b > a || b == 1f })
        assertTrue(v.all { it <= 1f })
        assertEquals((1 - exp(-1.0)).toFloat(), DensityCalculator.normalize(8.0), 1e-5f)
    }
    @Test fun liveRaw_counts() {
        val s = LiveSnapshot(LiveCell(Radio.LTE, 1, 1, 1, 1, null, -90, true), List(3) { LiveCell(Radio.LTE, 1, 1, null, null, it, -100, false) }, 4, 0)
        assertEquals(3 * 0.8 + 1.0, DensityCalculator.liveRaw(s), 1e-9)
        assertEquals(0.0, DensityCalculator.liveRaw(LiveSnapshot.EMPTY), 0.0)
    }
}

class SmootherTest {
    @Test fun smoother_firstValue_passthrough() = assertEquals(0.7f, Smoother().update(0.7f, 0), 0f)
    @Test fun smoother_stepChange_after20s_about63pct() {
        val s = Smoother()
        s.update(0f, 0)
        var v = 0f
        for (t in 1000L..20_000L step 1000) v = s.update(1f, t)
        assertEquals(0.632f, v, 0.02f)
    }
    @Test fun smoother_noLargeJump_in1s() {
        val s = Smoother()
        s.update(0f, 0)
        assertTrue(s.update(1f, 1000) < 0.06f)
    }
}

class ControlMapperTest {
    private val live = LiveSnapshot(LiveCell(Radio.LTE, 238, 1, 1, 1, null, -92, true), emptyList(), 4, 0)

    @Test fun mapper_noLocation_usesLiveFallback() {
        val c = ControlMapper().map(listOf(siteAt(0.0)), true, null, 2000.0, live, false, 0, 0.8f, 0)
        assertEquals(DensityCalculator.normalize(1.0), c.density, 1e-5f) // liveRaw = serving only
        assertEquals(5000f, c.nearestM, 0f)
        assertEquals(0.5f, c.signal, 1e-4f)
        assertEquals(4, c.netType)
    }

    @Test fun mapper_withDb_usesDbDensity() {
        val sites = List(10) { siteAt(0.0, "s$it") }
        val c = ControlMapper().map(sites, true, user, 2000.0, live, false, 0, 0.8f, 0)
        assertEquals(DensityCalculator.normalize(10.0), c.density, 1e-5f)
    }

    @Test fun mapper_noDbData_usesLiveFallback() {
        val c = ControlMapper().map(emptyList(), false, user, 2000.0, live, false, 0, 0.8f, 0)
        assertEquals(DensityCalculator.normalize(1.0), c.density, 1e-5f)
    }

    @Test fun mapper_nearest_noSites_is5000() =
        assertEquals(5000f, ControlMapper().map(emptyList(), true, user, 2000.0, live, false, 0, 1f, 0).nearestM, 0f)

    @Test fun mapper_nearest_distance() =
        assertEquals(500f, ControlMapper().map(listOf(siteAt(500 / M_PER_DEG)), true, user, 2000.0, live, false, 0, 1f, 0).nearestM, 5f)

    @Test fun mapper_events_passThrough() {
        val c = ControlMapper().map(emptyList(), true, user, 2000.0, live, true, 2, 0.3f, 0)
        assertTrue(c.handover); assertTrue(c.neighbourEvent); assertEquals(0.3f, c.master, 0f)
    }
}
