package io.github.warnizz.sniffer.ar

import io.github.warnizz.sniffer.geo.elevationDeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class ArProjectorTest {
    /** Phone upright in portrait, back camera facing north. */
    private val uprightNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
    private val w = 1080; private val h = 1920; private val fov = 60.0
    private val f = (w / 2) / tan(Math.toRadians(fov / 2))

    @Test fun project_ahead_center() {
        val p = ArProjector.project(uprightNorth, 0.0, 0.0, w, h, fov)!!
        assertEquals(540f, p.x, 1f); assertEquals(960f, p.y, 1f)
    }

    @Test fun project_10degRight() {
        val p = ArProjector.project(uprightNorth, 10.0, 0.0, w, h, fov)!!
        assertEquals((540 + f * tan(Math.toRadians(10.0))).toFloat(), p.x, 1f)
        assertEquals(960f, p.y, 1f)
    }

    @Test fun project_10degLeft_viaBearing350() {
        val p = ArProjector.project(uprightNorth, 350.0, 0.0, w, h, fov)!!
        assertEquals((540 - f * tan(Math.toRadians(10.0))).toFloat(), p.x, 1f)
    }

    @Test fun project_5degUp_aboveCenter() {
        val p = ArProjector.project(uprightNorth, 0.0, 5.0, w, h, fov)!!
        assertTrue(p.y < 960f)
        assertEquals((960 - f * tan(Math.toRadians(5.0))).toFloat(), p.y, 1f)
    }

    @Test fun project_behindCamera_null() = assertNull(ArProjector.project(uprightNorth, 180.0, 0.0, w, h, fov))

    @Test fun project_outsideFov_null() = assertNull(ArProjector.project(uprightNorth, 90.0, 0.0, w, h, fov))

    @Test fun project_veryCloseTower_noNaN() {
        val p = ArProjector.project(uprightNorth, 0.0, elevationDeg(0.0), w, h, fov)
        if (p != null) assertTrue(p.x.isFinite() && p.y.isFinite())
    }

    @Test fun project_closeTowerSeenWhenTiltedUp() {
        // Tilt phone back 60°: camera points 60° above the horizon to the north.
        val c = Math.cos(Math.toRadians(60.0)).toFloat(); val s = Math.sin(Math.toRadians(60.0)).toFloat()
        // device axes in world: x = East, y = (0, -sin, cos) tilted, z = (0, -cos, -sin)... built as columns
        val r = floatArrayOf(1f, 0f, 0f, 0f, -s, -c, 0f, c, -s)
        val p = ArProjector.project(r, 0.0, 60.0, w, h, fov)
        assertNotNull(p)
        assertEquals(960f, p!!.y, 2f)
    }
}
