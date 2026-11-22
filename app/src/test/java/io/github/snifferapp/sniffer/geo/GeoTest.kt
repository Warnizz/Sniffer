package io.github.snifferapp.sniffer.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.PI

class GeoTest {
    @Test fun distance_copenhagenToMalmo() {
        val d = distanceM(LatLon(55.6761, 12.5683), LatLon(55.6050, 13.0038))
        assertEquals(28_400.0, d, 1_000.0)
    }

    @Test fun bearing_dueEast_is90() = assertEquals(90.0, bearingDeg(LatLon(0.0, 0.0), LatLon(0.0, 1.0)), 0.01)

    @Test fun bearing_dueNorth_is0() = assertEquals(0.0, bearingDeg(LatLon(55.0, 12.0), LatLon(56.0, 12.0)), 0.01)

    @Test fun bearing_dueWest_is270() = assertEquals(270.0, bearingDeg(LatLon(0.0, 1.0), LatLon(0.0, 0.0)), 0.01)

    @Test fun elevation_100m() = assertEquals(atan2(28.5, 100.0) * 180 / PI, elevationDeg(100.0), 0.1)

    @Test fun elevation_zeroDistance_clampedNoNaN() {
        val e = elevationDeg(0.0)
        assertTrue(e.isFinite())
        assertEquals(80.0, e, 1.0)
    }
}
