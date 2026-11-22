package io.github.warnizz.sniffer.live

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.distanceM
import io.github.warnizz.sniffer.towers.TowerSite

data class Match(val site: TowerSite, val probable: Boolean)

private const val PCI_MATCH_RADIUS_M = 3000.0

/** Finds the database site for a live cell: exact on identity, else nearest same-PCI site (probable). */
fun matchLive(cell: LiveCell, sites: List<TowerSite>, user: LatLon?): Match? {
    if (cell.cellId != null) {
        val exact = sites.firstOrNull { s ->
            s.cells.any { it.mcc == cell.mcc && it.mnc == cell.mnc && it.area == cell.area && it.cellId == cell.cellId }
        }
        if (exact != null) return Match(exact, probable = false)
    }
    val pci = cell.pci ?: return null
    user ?: return null
    return sites
        .filter { s -> s.cells.any { it.mcc == cell.mcc && it.mnc == cell.mnc && it.radio == cell.radio && it.unit == pci } }
        .map { it to distanceM(user, it.pos) }
        .filter { it.second <= PCI_MATCH_RADIUS_M }
        .minByOrNull { it.second }
        ?.let { Match(it.first, probable = true) }
}
