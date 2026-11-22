package io.github.warnizz.sniffer.towers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.zip.GZIPInputStream

sealed interface ImportResult {
    data class Ok(val rows: Int) : ImportResult
    data class Failed(val reason: String) : ImportResult
    data object NotAvailable : ImportResult
}

const val MIRROR_BASE_URL = "https://github.com/Warnizz/Sniffer/releases/download/towers-latest"

interface CountrySource {
    suspend fun index(): CountryIndex?
    suspend fun import(mcc: Int, progress: (rows: Int) -> Unit = {}): ImportResult
    suspend fun updateAvailable(rec: CountryRecord): Boolean
}

/** Downloads a country file from the project's mirror and streams it into the store. */
class CountryImporter(
    private val http: OkHttpClient,
    private val store: TowerStore,
    private val nowMs: () -> Long,
    private val baseUrl: String = MIRROR_BASE_URL,
) : CountrySource {
    /** The mirror's index, or null when it can't be fetched. */
    override suspend fun index(): CountryIndex? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$baseUrl/index.json").build()).execute().use { resp ->
                if (!resp.isSuccessful) null else parseCountryIndex(resp.body!!.string())
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null // malformed JSON
        }
    }

    /**
     * Imports one country. The country record is written only after every row is stored, so an
     * interrupted import is never mistaken for a complete one.
     */
    override suspend fun import(mcc: Int, progress: (rows: Int) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        val idx = index() ?: return@withContext ImportResult.Failed("Couldn't reach the tower data mirror")
        val file = idx.countries[mcc] ?: return@withContext ImportResult.NotAvailable
        try {
            http.newCall(Request.Builder().url("$baseUrl/${file.file}").build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ImportResult.Failed("Download failed (HTTP ${resp.code})")
                var rows = 0
                GZIPInputStream(resp.body!!.byteStream()).bufferedReader().useLines { lines ->
                    parseCountryCsv(lines).chunked(BATCH).forEach { batch ->
                        ensureActive()
                        store.upsertCells(batch)
                        rows += batch.size
                        progress(rows)
                    }
                }
                store.putCountry(CountryRecord(mcc, rows, nowMs(), idx.generated))
                ImportResult.Ok(rows)
            }
        } catch (e: IOException) {
            ImportResult.Failed("Download interrupted: ${e.message ?: "network error"}")
        }
    }

    /** True when the mirror has a newer export than the one imported (ISO dates compare as strings). */
    override suspend fun updateAvailable(rec: CountryRecord): Boolean {
        val idx = index() ?: return false
        return idx.countries.containsKey(rec.mcc) && idx.generated > rec.generated
    }

    private companion object { const val BATCH = 2000 }
}
