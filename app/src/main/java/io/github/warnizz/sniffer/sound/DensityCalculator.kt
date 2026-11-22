package io.github.warnizz.sniffer.sound

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.distanceM
import io.github.warnizz.sniffer.live.LiveSnapshot
import io.github.warnizz.sniffer.towers.TowerSite
import kotlin.math.exp

object DensityCalculator {
    /** Σ 1/(1+(d/250)²) over sites within [radiusM]: a site on top of you counts 1, one at 250 m counts ½. */
    fun raw(sites: List<TowerSite>, user: LatLon, radiusM: Double): Double = sites.sumOf { s ->
        val d = distanceM(user, s.pos)
        if (d > radiusM) 0.0 else 1.0 / (1.0 + (d / 250.0).let { it * it })
    }

    /** Fallback when there is no database data: what the phone hears. */
    fun liveRaw(s: LiveSnapshot): Double = 0.8 * s.neighbours.size + if (s.serving != null) 1.0 else 0.0

    /** Soft saturation onto 0..1. raw 8 ≈ 0.63. */
    fun normalize(raw: Double): Float = (1.0 - exp(-raw / 8.0)).toFloat().coerceIn(0f, 1f)
}
