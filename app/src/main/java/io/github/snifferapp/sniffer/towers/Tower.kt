package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon

enum class Radio { GSM, UMTS, LTE, NR, CDMA, UNKNOWN }

/**
 * One cell from the tower database. [area] is LAC (GSM/UMTS) or TAC (LTE/NR).
 * [unit] is PSC/PCI when known (OpenCelliD CSV exports have it; the area API does not).
 */
data class Cell(
    val radio: Radio,
    val mcc: Int,
    val mnc: Int,
    val area: Int,
    val cellId: Long,
    val unit: Int?,
    val pos: LatLon,
    val rangeM: Int,
    val samples: Int,
)

/** Cells of one carrier that sit on the same mast. */
data class TowerSite(val id: String, val mcc: Int, val mnc: Int, val pos: LatLon, val cells: List<Cell>)
