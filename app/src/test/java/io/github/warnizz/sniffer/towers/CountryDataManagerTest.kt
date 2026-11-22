package io.github.warnizz.sniffer.towers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeCountrySource(private val store: TowerStore) : CountrySource {
    var generated = "2026-11-03"
    val imported = mutableListOf<Int>()
    override suspend fun index() = CountryIndex(generated, mapOf(238 to CountryFile(238, "238.csv.gz", 4_200_000, 3)))
    override suspend fun import(mcc: Int, progress: (Int) -> Unit): ImportResult {
        if (mcc != 238) return ImportResult.NotAvailable
        imported += mcc
        store.putCountry(CountryRecord(mcc, 3, 0L, generated))
        return ImportResult.Ok(3)
    }
    override suspend fun updateAvailable(rec: CountryRecord) = generated > rec.generated
}

class CountryDataManagerTest {
    private val day = 24 * 3600 * 1000L
    private val store = FakeTowerStore()
    private val source = FakeCountrySource(store)
    private var metered = false
    private var lastCheck: Long? = null
    private var now = 100 * day

    private fun manager() = CountryDataManager(source, store, { metered }, { lastCheck }, { lastCheck = it }, { now })

    @Test fun download_refreshesRecords() = runTest {
        val m = manager()
        m.download(238)
        assertEquals(listOf(238), m.state.value.records.map { it.mcc })
        assertNull(m.state.value.busyMcc)
        assertNull(m.state.value.error)
    }

    @Test fun download_notAvailable_setsError() = runTest {
        val m = manager()
        m.download(999)
        assertEquals("No tower data for MCC 999 in the mirror yet", m.state.value.error)
    }

    @Test fun autoUpdate_skipsOnMetered() = runTest {
        store.putCountry(CountryRecord(238, 3, 0L, "2026-10-03"))
        metered = true
        manager().autoUpdate()
        assertEquals(emptyList<Int>(), source.imported)
    }

    @Test fun autoUpdate_skipsWithin7Days() = runTest {
        store.putCountry(CountryRecord(238, 3, 0L, "2026-10-03"))
        lastCheck = now - 6 * day
        manager().autoUpdate()
        assertEquals(emptyList<Int>(), source.imported)
    }

    @Test fun autoUpdate_updatesStaleCountries() = runTest {
        store.putCountry(CountryRecord(238, 3, 0L, "2026-10-03"))
        lastCheck = now - 8 * day
        manager().autoUpdate()
        assertEquals(listOf(238), source.imported)
        assertEquals(now, lastCheck)
    }

    @Test fun remove_deletesRecord() = runTest {
        val m = manager()
        m.download(238)
        m.remove(238)
        assertEquals(emptyList<CountryRecord>(), m.state.value.records)
    }

    @Test fun download_sourceThrows_errorAndNotBusy() = runTest {
        val throwing = object : CountrySource by source {
            override suspend fun import(mcc: Int, progress: (Int) -> Unit): ImportResult = throw android.database.sqlite.SQLiteFullException("disk full")
        }
        val m = CountryDataManager(throwing, store, { false }, { null }, { }, { now })
        m.download(238)
        assertNull(m.state.value.busyMcc)
        assertEquals("Not enough storage space for this country's tower data", m.state.value.error)
    }
}
