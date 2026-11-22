package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.geo.TileId
import io.github.snifferapp.sniffer.geo.tileBbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface FetchResult {
    data class Ok(val cells: List<Cell>) : FetchResult
    data object InvalidKey : FetchResult
    data object RateLimited : FetchResult
    data class Failed(val reason: String) : FetchResult
}

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable
private data class AreaResponse(val cells: List<CellDto> = emptyList(), val error: String? = null, val code: Int? = null)

@Serializable
private data class CellDto(
    val lat: Double = 0.0, val lon: Double = 0.0, val mcc: Int = 0, val mnc: Int = 0, val lac: Int = 0,
    val cellid: Long = 0, val range: Int = 0, val samples: Int = 0, val radio: String = "",
)

private fun radioOf(s: String) = when (s.uppercase()) {
    "GSM" -> Radio.GSM; "UMTS" -> Radio.UMTS; "LTE" -> Radio.LTE; "NR" -> Radio.NR; "CDMA" -> Radio.CDMA
    else -> Radio.UNKNOWN
}

private fun AreaResponse.validCells(): List<Cell> = cells
    .asSequence()
    .filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 && !(it.lat == 0.0 && it.lon == 0.0) }
    .map { Cell(radioOf(it.radio), it.mcc, it.mnc, it.lac, it.cellid, null, LatLon(it.lat, it.lon), it.range, it.samples) }
    .distinctBy { listOf(it.radio, it.mcc, it.mnc, it.area, it.cellId) }
    .toList()

/** Parses a getInArea JSON page, dropping junk and duplicate rows. */
fun parseArea(body: String): List<Cell> = json.decodeFromString<AreaResponse>(body).validCells()

class OpenCellIdClient(private val http: OkHttpClient, private val baseUrl: String = "https://opencellid.org") {

    suspend fun fetchTile(key: String, tile: TileId): FetchResult = withContext(Dispatchers.IO) {
        val all = mutableListOf<Cell>()
        for (page in 0 until MAX_PAGES) {
            val result = fetchPage(key, tile, page * PAGE)
            if (result !is PageResult.Cells) return@withContext (result as PageResult.Error).result
            all += result.cells
            if (result.rawCount < PAGE) break
        }
        FetchResult.Ok(all.distinctBy { listOf(it.radio, it.mcc, it.mnc, it.area, it.cellId) })
    }

    private sealed interface PageResult {
        data class Cells(val cells: List<Cell>, val rawCount: Int) : PageResult
        data class Error(val result: FetchResult) : PageResult
    }

    private fun fetchPage(key: String, tile: TileId, offset: Int): PageResult {
        val b = tileBbox(tile).joinToString(",") { fmt(it) }
        val url = "$baseUrl/cell/getInArea".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("BBOX", b)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", PAGE.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString<AreaResponse>(body) }.getOrNull()
                when {
                    resp.code == 401 || parsed?.code == 2 -> PageResult.Error(FetchResult.InvalidKey)
                    resp.code == 429 || parsed?.code == 7 -> PageResult.Error(FetchResult.RateLimited)
                    parsed?.code == 1 -> PageResult.Cells(emptyList(), 0) // "Cell not found" on HTTP 200
                    !resp.isSuccessful || parsed == null || parsed.error != null ->
                        PageResult.Error(FetchResult.Failed("HTTP ${resp.code} ${parsed?.error.orEmpty()}".trim()))
                    else -> PageResult.Cells(parsed.validCells(), parsed.cells.size)
                }
            }
        } catch (e: IOException) {
            PageResult.Error(FetchResult.Failed(e.message ?: "network error"))
        }
    }

    private companion object {
        const val PAGE = 50 // API maximum
        const val MAX_PAGES = 10 // caps one tile at 500 cells / 10 request credits
        fun fmt(d: Double): String = BigDecimal(d).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
