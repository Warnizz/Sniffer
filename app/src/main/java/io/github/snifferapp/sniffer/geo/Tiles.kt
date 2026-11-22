package io.github.snifferapp.sniffer.geo

import kotlin.math.cos
import kotlin.math.floor

/** Fixed 0.015° grid. Each tile stays under OpenCelliD's 4 km² bbox limit at any latitude. */
const val TILE_DEG = 0.015

data class TileId(val x: Int, val y: Int)

fun tileOf(p: LatLon): TileId = TileId(floor(p.lon / TILE_DEG).toInt(), floor(p.lat / TILE_DEG).toInt())

/** [latMin, lonMin, latMax, lonMax] */
fun tileBbox(t: TileId): DoubleArray =
    doubleArrayOf(t.y * TILE_DEG, t.x * TILE_DEG, (t.y + 1) * TILE_DEG, (t.x + 1) * TILE_DEG)

fun tilesCovering(center: LatLon, radiusM: Double): List<TileId> {
    val dLat = radiusM / 111_320.0
    val dLon = radiusM / (111_320.0 * cos(Math.toRadians(center.lat)).coerceAtLeast(0.01))
    val min = tileOf(LatLon(center.lat - dLat, center.lon - dLon))
    val max = tileOf(LatLon(center.lat + dLat, center.lon + dLon))
    return (min.y..max.y).flatMap { y -> (min.x..max.x).map { x -> TileId(x, y) } }
}
