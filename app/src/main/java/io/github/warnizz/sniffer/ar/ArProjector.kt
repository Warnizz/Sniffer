package io.github.warnizz.sniffer.ar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class ScreenPoint(val x: Float, val y: Float)

object ArProjector {
    /**
     * Projects a world direction (bearing from north, elevation above horizon) onto the screen.
     * [r] is the row-major 3×3 device→world (East-North-Up) matrix from
     * SensorManager.getRotationMatrixFromVector. The back camera looks along device −Z.
     * Returns null when the direction is behind the camera or well off screen.
     */
    fun project(r: FloatArray, bearingDeg: Double, elevationDeg: Double, viewW: Int, viewH: Int, hFovDeg: Double): ScreenPoint? {
        val b = Math.toRadians(bearingDeg)
        val e = Math.toRadians(elevationDeg)
        val wx = sin(b) * cos(e); val wy = cos(b) * cos(e); val wz = sin(e)
        // d = Rᵀ·w
        val dx = r[0] * wx + r[3] * wy + r[6] * wz
        val dy = r[1] * wx + r[4] * wy + r[7] * wz
        val dz = r[2] * wx + r[5] * wy + r[8] * wz
        if (dz >= -1e-3) return null
        val f = (viewW / 2.0) / tan(Math.toRadians(hFovDeg / 2))
        val x = viewW / 2.0 + f * (dx / -dz)
        val y = viewH / 2.0 - f * (dy / -dz)
        if (x < -0.1 * viewW || x > 1.1 * viewW || y < -0.1 * viewH || y > 1.1 * viewH) return null
        return ScreenPoint(x.toFloat(), y.toFloat())
    }
}
