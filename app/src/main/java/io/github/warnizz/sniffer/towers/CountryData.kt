package io.github.warnizz.sniffer.towers

import io.github.warnizz.sniffer.geo.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One country file in the mirror's index.json. */
data class CountryFile(val mcc: Int, val file: String, val bytes: Long, val rows: Int)

data class CountryIndex(val generated: String, val countries: Map<Int, CountryFile>)

/** A country that has been imported into the local store. */
data class CountryRecord(val mcc: Int, val rows: Int, val importedAtMs: Long, val generated: String)

@Serializable private data class IndexDto(val generated: String = "", val countries: Map<String, FileDto> = emptyMap())
@Serializable private data class FileDto(val file: String, val bytes: Long = 0, val rows: Int = 0)

private val json = Json { ignoreUnknownKeys = true }

fun parseCountryIndex(text: String): CountryIndex {
    val dto = json.decodeFromString<IndexDto>(text)
    return CountryIndex(
        dto.generated,
        dto.countries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to CountryFile(it, v.file, v.bytes, v.rows) } }.toMap(),
    )
}

private fun radioOf(s: String) = when (s.uppercase()) {
    "GSM" -> Radio.GSM; "UMTS" -> Radio.UMTS; "LTE" -> Radio.LTE; "NR" -> Radio.NR; "CDMA" -> Radio.CDMA
    else -> Radio.UNKNOWN
}

/** Parses mirror CSV lines (radio,mcc,net,area,cell,unit,lon,lat,range,samples); skips the header and junk. */
fun parseCountryCsv(lines: Sequence<String>): Sequence<Cell> = lines.mapNotNull { line ->
    val f = line.trim().split(',')
    if (f.size < 10 || f[0] == "radio") return@mapNotNull null
    val mcc = f[1].toIntOrNull() ?: return@mapNotNull null
    val mnc = f[2].toIntOrNull() ?: return@mapNotNull null
    val area = f[3].toIntOrNull() ?: return@mapNotNull null
    val cellId = f[4].toLongOrNull() ?: return@mapNotNull null
    val lon = f[6].toDoubleOrNull() ?: return@mapNotNull null
    val lat = f[7].toDoubleOrNull() ?: return@mapNotNull null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
    Cell(radioOf(f[0]), mcc, mnc, area, cellId, f[5].toIntOrNull(), LatLon(lat, lon), f[8].toIntOrNull() ?: 0, f[9].toIntOrNull() ?: 0)
}
