package io.github.warnizz.sniffer.towers

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class CountryImporterTest {
    private val server = MockWebServer()
    private val store = FakeTowerStore()
    private val index = """{"generated":"2026-11-03","countries":{"238":{"file":"238.csv.gz","bytes":100,"rows":3}}}"""
    private val csv = """radio,mcc,net,area,cell,unit,lon,lat,range,samples
LTE,238,1,1001,1,10,12.568,55.676,800,12
LTE,238,1,1001,2,11,12.569,55.676,800,12
GSM,238,2,2002,3,,12.570,55.677,1500,3
"""
    private fun gz(s: String): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(s.toByteArray()) } }.toByteArray()

    private fun serve(routes: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = routes[request.path] ?: MockResponse().setResponseCode(404)
        }
        server.start()
    }

    private fun importer() = CountryImporter(OkHttpClient(), store, { 42L }, server.url("/").toString().trimEnd('/'))

    @After fun tearDown() = server.shutdown()

    @Test fun importer_importsRowsAndRecords() = runTest {
        serve(mapOf("/index.json" to MockResponse().setBody(index), "/238.csv.gz" to MockResponse().setBody(Buffer().write(gz(csv)))))
        val r = importer().import(238)
        assertEquals(ImportResult.Ok(3), r)
        assertEquals(3, store.cells.size)
        assertEquals(CountryRecord(238, 3, 42L, "2026-11-03"), store.records[238])
    }

    @Test fun importer_mccNotInIndex_notAvailable() = runTest {
        serve(mapOf("/index.json" to MockResponse().setBody(index)))
        assertEquals(ImportResult.NotAvailable, importer().import(240))
    }

    @Test fun importer_failedDownload_notMarkedImported() = runTest {
        serve(mapOf("/index.json" to MockResponse().setBody(index), "/238.csv.gz" to MockResponse().setResponseCode(500)))
        assertTrue(importer().import(238) is ImportResult.Failed)
        assertFalse(store.records.containsKey(238))
    }

    @Test fun importer_indexMissing_failed() = runTest {
        serve(emptyMap())
        assertTrue(importer().import(238) is ImportResult.Failed)
    }

    @Test fun importer_truncatedGzip_failed_noRecord() = runTest {
        val bytes = gz(csv).let { it.copyOf(it.size - 12) }
        serve(mapOf("/index.json" to MockResponse().setBody(index), "/238.csv.gz" to MockResponse().setBody(Buffer().write(bytes))))
        assertTrue(importer().import(238) is ImportResult.Failed)
        assertFalse(store.records.containsKey(238))
    }

    @Test fun updateAvailable_newerGenerated_true() = runTest {
        serve(mapOf("/index.json" to MockResponse().setBody(index)))
        val imp = importer()
        assertTrue(imp.updateAvailable(CountryRecord(238, 3, 1L, "2026-10-03")))
        assertFalse(imp.updateAvailable(CountryRecord(238, 3, 1L, "2026-11-03")))
    }
}
