package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.geo.TileId
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCellIdParseTest {
    private val fixture = javaClass.classLoader!!.getResource("opencellid_area.json")!!.readText()

    @Test fun parse_readsCells() {
        val cells = parseArea(fixture)
        assertEquals(5, cells.size)
        val first = cells.first()
        assertEquals(Radio.LTE, first.radio)
        assertEquals(238, first.mcc); assertEquals(1, first.mnc); assertEquals(1001, first.area)
        assertEquals(2000001L, first.cellId)
        assertEquals(LatLon(55.676, 12.568), first.pos)
        assertEquals(68719476000L, cells.single { it.radio == Radio.NR }.cellId)
    }

    @Test fun parse_dropsJunkRows() {
        val cells = parseArea(fixture)
        assertTrue(cells.none { it.pos.lat == 0.0 && it.pos.lon == 0.0 })
        assertTrue(cells.none { it.pos.lat > 90 || it.pos.lon > 180 })
        assertEquals(1, cells.count { it.cellId == 2000001L })
    }

    @Test fun parse_unknownRadio_mapsToUnknown() {
        val cells = parseArea("""{"count":1,"cells":[{"lat":1.0,"lon":1.0,"mcc":1,"mnc":1,"lac":1,"cellid":1,"range":1,"samples":1,"radio":"TETRA"}]}""")
        assertEquals(Radio.UNKNOWN, cells.single().radio)
    }

    @Test fun parse_empty_isEmpty() = assertEquals(0, parseArea("""{"count":0,"cells":[]}""").size)

    private fun withServer(vararg responses: MockResponse, block: suspend (OpenCellIdClient, MockWebServer) -> Unit) = runTest {
        val server = MockWebServer()
        responses.forEach(server::enqueue)
        server.start()
        try { block(OpenCellIdClient(OkHttpClient(), server.url("/").toString().trimEnd('/')), server) } finally { server.shutdown() }
    }

    @Test fun fetch_invalidKey() = withServer(MockResponse().setResponseCode(401).setBody("""{"error":"API Key not known","code":2}""")) { c, _ ->
        assertEquals(FetchResult.InvalidKey, c.fetchTile("bad", TileId(1, 1)))
    }

    @Test fun fetch_rateLimited() = withServer(MockResponse().setResponseCode(429).setBody("""{"error":"Daily limit exceeded","code":7}""")) { c, _ ->
        assertEquals(FetchResult.RateLimited, c.fetchTile("k", TileId(1, 1)))
    }

    @Test fun fetch_cellNotFoundOn200_isEmptyOk() = withServer(MockResponse().setBody("""{"error":"Cell not found","code":1}""")) { c, _ ->
        assertEquals(FetchResult.Ok(emptyList()), c.fetchTile("k", TileId(1, 1)))
    }

    @Test fun fetch_serverError_failed() = withServer(MockResponse().setResponseCode(500).setBody("""{"error":"x","code":5}""")) { c, _ ->
        assertTrue(c.fetchTile("k", TileId(1, 1)) is FetchResult.Failed)
    }

    @Test fun fetch_requestShape() = withServer(MockResponse().setBody("""{"count":0,"cells":[]}""")) { c, s ->
        c.fetchTile("KEY", TileId(2, 3))
        val url = s.takeRequest().requestUrl!!
        assertEquals("/cell/getInArea", url.encodedPath)
        assertEquals("KEY", url.queryParameter("key"))
        assertEquals("json", url.queryParameter("format"))
        assertEquals("50", url.queryParameter("limit"))
        assertEquals("0.045,0.03,0.06,0.045", url.queryParameter("BBOX"))
    }

    @Test fun fetch_pagesWhileFull() {
        val row = { i: Int -> """{"lat":1.0,"lon":1.0,"mcc":1,"mnc":1,"lac":1,"cellid":$i,"range":1,"samples":1,"radio":"LTE"}""" }
        val page1 = """{"count":50,"cells":[${(1..50).joinToString(",") { row(it) }}]}"""
        val page2 = """{"count":3,"cells":[${(51..53).joinToString(",") { row(it) }}]}"""
        withServer(MockResponse().setBody(page1), MockResponse().setBody(page2)) { c, s ->
            val r = c.fetchTile("k", TileId(1, 1)) as FetchResult.Ok
            assertEquals(53, r.cells.size)
            s.takeRequest()
            assertEquals("50", s.takeRequest().requestUrl!!.queryParameter("offset"))
        }
    }
}
