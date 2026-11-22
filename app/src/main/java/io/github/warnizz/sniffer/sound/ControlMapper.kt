package io.github.warnizz.sniffer.sound

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.distanceM
import io.github.warnizz.sniffer.live.LiveSnapshot
import io.github.warnizz.sniffer.towers.TowerSite

/** Blends database density (the bed) with live data (the texture) into [SoundControls]. */
class ControlMapper(private val smoother: Smoother = Smoother()) {
    fun map(
        sites: List<TowerSite>, hasDbData: Boolean, user: LatLon?, radiusM: Double,
        live: LiveSnapshot, handover: Boolean, newNeighbours: Int, master: Float, nowMs: Long,
    ): SoundControls {
        val raw = if (hasDbData && user != null) DensityCalculator.raw(sites, user, radiusM) else DensityCalculator.liveRaw(live)
        val nearest = if (user == null) null else sites.minOfOrNull { distanceM(user, it.pos) }
        return SoundControls(
            density = smoother.update(DensityCalculator.normalize(raw), nowMs),
            nearestM = (nearest ?: NO_TOWER_M).toFloat(),
            signal = live.signal,
            netType = live.netType,
            neighbours = live.neighbours.size,
            handover = handover,
            neighbourEvent = newNeighbours > 0,
            master = master,
        )
    }

    private companion object { const val NO_TOWER_M = 5000.0 }
}
