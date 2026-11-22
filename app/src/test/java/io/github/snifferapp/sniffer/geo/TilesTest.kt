package io.github.snifferapp.sniffer.geo

import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {
    private val cph = LatLon(55.6761, 12.5683)

    @Test fun tilesCovering_2km_containsCenterTile() {
        assertTrue(tileOf(cph) in tilesCovering(cph, 2000.0))
    }

    @Test fun tilesCovering_2km_size() {
        val n = tilesCovering(cph, 2000.0).size
        assertTrue("got $n", n in 9..30)
    }

    @Test fun tileBbox_containsPoint() {
        val b = tileBbox(tileOf(cph))
        assertTrue(cph.lat >= b[0] && cph.lon >= b[1] && cph.lat < b[2] && cph.lon < b[3])
    }

    @Test fun tileBbox_areaUnder4km2_atEquatorAndLat60() {
        for (p in listOf(LatLon(0.001, 0.001), LatLon(60.0, 10.0), LatLon(-33.9, 18.4))) {
            val b = tileBbox(tileOf(p))
            val h = distanceM(LatLon(b[0], b[1]), LatLon(b[2], b[1]))
            val w = distanceM(LatLon(b[0], b[1]), LatLon(b[0], b[3]))
            assertTrue("area ${h * w}", h * w < 4_000_000.0)
        }
    }

    @Test fun tileOf_negativeCoords_floor() {
        val t = tileOf(LatLon(-0.001, -0.001))
        assertTrue(t.x == -1 && t.y == -1)
    }
}
