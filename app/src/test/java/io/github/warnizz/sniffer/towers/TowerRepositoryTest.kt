package io.github.warnizz.sniffer.towers

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.TileId
import io.github.warnizz.sniffer.geo.tileOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTowerStore : TowerStore {
    val tiles = mutableMapOf<TileId, Long>()
    val cells = mutableListOf<Cell>()
    val records = mutableMapOf<Int, CountryRecord>()
    override suspend fun cellsInTiles(tiles: List<TileId>) = cells.filter { tileOf(it.pos) in tiles }
    override suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long) { upsertCells(cells); tiles[tile] = fetchedAtMs }
    override suspend fun tileFetchedAt(tile: TileId) = tiles[tile]
    override suspend fun upsertCells(cells: List<Cell>) {
        for (c in cells) { this.cells.removeAll { it.radio == c.radio && it.mcc == c.mcc && it.mnc == c.mnc && it.area == c.area && it.cellId == c.cellId }; this.cells += c }
    }
    override suspend fun putCountry(record: CountryRecord) { records[record.mcc] = record }
    override suspend fun countries() = records.values.toList()
    override suspend fun removeCountry(mcc: Int) { cells.removeAll { it.mcc == mcc }; records.remove(mcc) }
}

class TowerRepositoryTest {
    private val day = 24 * 3600 * 1000L
    private val center = LatLon(55.6761, 12.5683)
    private fun cellAt(lat: Double, lon: Double, id: Long = 1) = Cell(Radio.LTE, 238, 1, 1, id, null, LatLon(lat, lon), 500, 1)

    private var now = 100 * day
    private val store = FakeTowerStore()
    private val fetched = mutableListOf<TileId>()
    private var fetchResult: (TileId) -> FetchResult = { FetchResult.Ok(emptyList()) }
    private val repo = TowerRepository(store, { _, t -> fetched += t; fetchResult(t) }, { now })

    @Test fun repository_freshTile_noFetch() = runTest {
        repo.sitesNear(center, 2000.0, "k")
        val n = fetched.size
        now += 29 * day
        repo.sitesNear(center, 2000.0, "k")
        assertEquals(n, fetched.size)
    }

    @Test fun repository_staleTile_refetches() = runTest {
        repo.sitesNear(center, 2000.0, "k")
        val n = fetched.size
        now += 31 * day
        repo.sitesNear(center, 2000.0, "k")
        assertEquals(2 * n, fetched.size)
    }

    @Test fun repository_noKey_returnsCachedOnly_statusOk() = runTest {
        store.putTile(tileOf(center), listOf(cellAt(center.lat, center.lon)), now)
        val r = repo.sitesNear(center, 2000.0, null)
        assertEquals(0, fetched.size)
        assertEquals(DbStatus.OK, r.status) // cached/imported data is enough without a key
        assertEquals(1, r.sites.size)
        assertTrue(r.hasData)
    }

    @Test fun repository_noKeyNoCache_hasNoData() = runTest {
        assertFalse(repo.sitesNear(center, 2000.0, "").hasData)
    }

    @Test fun repository_fetchFails_keepsCacheAndBacksOff() = runTest {
        store.putTile(tileOf(center), listOf(cellAt(center.lat, center.lon)), now - 40 * day) // stale
        fetchResult = { FetchResult.Failed("offline") }
        val r1 = repo.sitesNear(center, 2000.0, "k")
        assertEquals(1, r1.sites.size)
        assertEquals(DbStatus.OFFLINE_PARTIAL, r1.status)
        val n = fetched.size
        now += 9 * 60_000
        repo.sitesNear(center, 2000.0, "k")
        assertEquals(n, fetched.size)
        now += 2 * 60_000
        repo.sitesNear(center, 2000.0, "k")
        assertTrue(fetched.size > n)
    }

    @Test fun repository_invalidKey_reportsKeyError() = runTest {
        fetchResult = { FetchResult.InvalidKey }
        val r = repo.sitesNear(center, 2000.0, "bad")
        assertEquals(DbStatus.INVALID_KEY, r.status)
        assertEquals(1, fetched.size)
    }

    @Test fun repository_rateLimited_stopsAndReports() = runTest {
        fetchResult = { FetchResult.RateLimited }
        val r = repo.sitesNear(center, 2000.0, "k")
        assertEquals(DbStatus.RATE_LIMITED, r.status)
        assertEquals(1, fetched.size)
    }

    @Test fun repository_filtersToRadius() = runTest {
        fetchResult = { t -> if (t == tileOf(center)) FetchResult.Ok(listOf(cellAt(center.lat, center.lon, 1), cellAt(center.lat + 0.0135, center.lon, 2))) else FetchResult.Ok(emptyList()) }
        val r = repo.sitesNear(center, 1000.0, "k") // second cell is ~1.5 km north
        assertEquals(1, r.sites.size)
        assertEquals(DbStatus.OK, r.status)
    }

    @Test fun repository_rateLimited_noRetryFor10Min() = runTest {
        fetchResult = { FetchResult.RateLimited }
        repo.sitesNear(center, 2000.0, "k")
        now += 5 * 60_000
        assertEquals(DbStatus.RATE_LIMITED, repo.sitesNear(center, 2000.0, "k").status)
        assertEquals(1, fetched.size)
    }

    @Test fun repository_invalidKey_retriesWhenKeyChanges() = runTest {
        fetchResult = { FetchResult.InvalidKey }
        repo.sitesNear(center, 2000.0, "bad")
        fetchResult = { FetchResult.Ok(emptyList()) }
        assertEquals(DbStatus.OK, repo.sitesNear(center, 2000.0, "good").status)
        assertTrue(fetched.size > 1)
    }

    @Test fun repository_noKey_withCountryData_ok() = runTest {
        store.upsertCells(listOf(cellAt(center.lat, center.lon)))
        val r = repo.sitesNear(center, 2000.0, null)
        assertEquals(DbStatus.OK, r.status)
        assertTrue(r.hasData)
        assertEquals(1, r.sites.size)
        assertEquals(0, fetched.size)
    }

    @Test fun repository_noKey_countryImported_emptyArea_isOkWithData() = runTest {
        store.putCountry(CountryRecord(238, 1000, 0L, "2026-11-03"))
        val r = repo.sitesNear(center, 2000.0, null)
        assertEquals(DbStatus.OK, r.status)
        assertTrue(r.hasData) // "no towers here" is real data, not a missing database
        assertEquals(0, r.sites.size)
    }
}
