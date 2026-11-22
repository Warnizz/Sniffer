package io.github.warnizz.sniffer.towers

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.distanceM

/** Groups cells of the same carrier lying within [mergeM] of a site's first cell into one site. */
fun clusterSites(cells: List<Cell>, mergeM: Double = 50.0): List<TowerSite> {
    val groups = mutableListOf<MutableList<Cell>>()
    for (c in cells) {
        val g = groups.firstOrNull { g ->
            val f = g.first()
            f.mcc == c.mcc && f.mnc == c.mnc && distanceM(f.pos, c.pos) <= mergeM
        }
        if (g != null) g += c else groups += mutableListOf(c)
    }
    return groups.map { g ->
        val f = g.first()
        TowerSite(
            id = "${f.mcc}-${f.mnc}-${f.area}-${f.cellId}",
            mcc = f.mcc, mnc = f.mnc,
            pos = LatLon(g.sumOf { it.pos.lat } / g.size, g.sumOf { it.pos.lon } / g.size),
            cells = g,
        )
    }
}
