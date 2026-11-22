package io.github.warnizz.sniffer.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle (haversine) distance in metres. */
fun distanceM(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Initial bearing from [from] to [to], degrees 0..360 with 0 = north. */
fun bearingDeg(from: LatLon, to: LatLon): Double {
    val phi1 = Math.toRadians(from.lat)
    val phi2 = Math.toRadians(to.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Angle above the horizon to the top of a mast, assuming flat ground. Distance is clamped to 5 m. */
fun elevationDeg(distanceM: Double, mastM: Double = 30.0, eyeM: Double = 1.5): Double =
    Math.toDegrees(atan2(mastM - eyeM, max(distanceM, 5.0)))
