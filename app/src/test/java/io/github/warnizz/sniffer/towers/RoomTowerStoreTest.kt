package io.github.warnizz.sniffer.towers

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.TileId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomTowerStoreTest {
    @Test fun room_putThenRead_roundTrips() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(), TowerDb::class.java)
            .allowMainThreadQueries().build()
        val store = RoomTowerStore(db)
        val cells = listOf(Cell(Radio.NR, 238, 20, 3003, 68719476000L, null, LatLon(55.68, 12.575), 400, 2))
        val t = io.github.warnizz.sniffer.geo.tileOf(cells[0].pos) // cells are stored under the tile of their position
        assertNull(store.tileFetchedAt(t))
        store.putTile(t, cells, 1234L)
        assertEquals(1234L, store.tileFetchedAt(t))
        assertEquals(cells, store.cellsInTiles(listOf(t, TileId(9, 9))))
        store.putTile(t, emptyList(), 5678L) // API refreshes never erase cells (imported ones included)
        assertEquals(cells, store.cellsInTiles(listOf(t)))
        assertEquals(5678L, store.tileFetchedAt(t))
        db.close()
    }

    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(), TowerDb::class.java)
        .allowMainThreadQueries().build()
    private fun cell(mcc: Int, id: Long, lat: Double = 55.676) = Cell(Radio.LTE, mcc, 1, 1, id, 5, LatLon(lat, 12.568), 500, 1)

    @Test fun store_upsert_keepsOtherCells() = runTest {
        val db = db(); val store = RoomTowerStore(db)
        val t = io.github.warnizz.sniffer.geo.tileOf(LatLon(55.676, 12.568))
        store.upsertCells(listOf(cell(238, 1)))
        store.upsertCells(listOf(cell(240, 2)))
        store.putTile(t, listOf(cell(238, 3)), 1L)
        assertEquals(setOf(1L, 2L, 3L), store.cellsInTiles(listOf(t)).map { it.cellId }.toSet())
        assertEquals(5, store.cellsInTiles(listOf(t)).first().unit)
        db.close()
    }

    @Test fun store_removeCountry() = runTest {
        val db = db(); val store = RoomTowerStore(db)
        val t = io.github.warnizz.sniffer.geo.tileOf(LatLon(55.676, 12.568))
        store.upsertCells(listOf(cell(238, 1), cell(240, 2)))
        store.putCountry(CountryRecord(238, 1, 10L, "2026-11-03"))
        store.putCountry(CountryRecord(240, 1, 10L, "2026-11-03"))
        store.removeCountry(238)
        assertEquals(listOf(2L), store.cellsInTiles(listOf(t)).map { it.cellId })
        assertEquals(listOf(240), store.countries().map { it.mcc })
        db.close()
    }
}
