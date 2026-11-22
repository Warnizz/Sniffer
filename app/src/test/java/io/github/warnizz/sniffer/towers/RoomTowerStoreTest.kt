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
        val t = TileId(5, 7)
        val cells = listOf(Cell(Radio.NR, 238, 20, 3003, 68719476000L, null, LatLon(55.68, 12.575), 400, 2))
        assertNull(store.tileFetchedAt(t))
        store.putTile(t, cells, 1234L)
        assertEquals(1234L, store.tileFetchedAt(t))
        assertEquals(cells, store.cellsInTiles(listOf(t, TileId(9, 9))))
        store.putTile(t, emptyList(), 5678L) // replacing a tile drops its old cells
        assertEquals(emptyList<Cell>(), store.cellsInTiles(listOf(t)))
        db.close()
    }
}
