package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteClustererTest {
    private fun cell(id: Long, mnc: Int, lat: Double, lon: Double) =
        Cell(Radio.LTE, 238, mnc, 1, id, null, LatLon(lat, lon), 500, 1)

    @Test fun cluster_sameCarrierWithin50m_merges() {
        val sites = clusterSites(listOf(cell(1, 1, 55.0, 12.0), cell(2, 1, 55.0001, 12.0), cell(3, 1, 55.0, 12.0002)))
        assertEquals(1, sites.size)
        assertEquals(3, sites.single().cells.size)
        assertEquals("238-1-1-1", sites.single().id)
    }

    @Test fun cluster_differentCarrierSameSpot_separate() {
        assertEquals(2, clusterSites(listOf(cell(1, 1, 55.0, 12.0), cell(2, 2, 55.0, 12.0))).size)
    }

    @Test fun cluster_sameCarrier200mApart_separate() {
        assertEquals(2, clusterSites(listOf(cell(1, 1, 55.0, 12.0), cell(2, 1, 55.0018, 12.0))).size)
    }

    @Test fun cluster_positionIsMean() {
        val s = clusterSites(listOf(cell(1, 1, 55.0, 12.0), cell(2, 1, 55.0002, 12.0))).single()
        assertEquals(55.0001, s.pos.lat, 1e-9)
    }
}
