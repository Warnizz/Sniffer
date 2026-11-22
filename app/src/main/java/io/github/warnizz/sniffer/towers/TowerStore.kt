package io.github.warnizz.sniffer.towers

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.TileId
import io.github.warnizz.sniffer.geo.tileOf

interface TowerStore {
    suspend fun cellsInTiles(tiles: List<TileId>): List<Cell>
    suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long)
    suspend fun tileFetchedAt(tile: TileId): Long?
    /** Inserts or replaces cells; never deletes. */
    suspend fun upsertCells(cells: List<Cell>)
    suspend fun putCountry(record: CountryRecord)
    suspend fun countries(): List<CountryRecord>
    /** Deletes every cell with this MCC and the country record. */
    suspend fun removeCountry(mcc: Int)
}

class RoomTowerStore(db: TowerDb) : TowerStore {
    private val dao = db.dao()

    override suspend fun cellsInTiles(tiles: List<TileId>): List<Cell> =
        tiles.flatMap { t -> dao.cellsInTile(t.x, t.y).map { it.toCell() } }

    override suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long) =
        dao.putTile(TileEntity(tile.x, tile.y, fetchedAtMs), cells.map { it.toEntity() })

    override suspend fun tileFetchedAt(tile: TileId): Long? = dao.tileFetchedAt(tile.x, tile.y)

    override suspend fun upsertCells(cells: List<Cell>) = dao.insertCells(cells.map { it.toEntity() })

    override suspend fun putCountry(record: CountryRecord) =
        dao.putCountry(CountryEntity(record.mcc, record.rows, record.importedAtMs, record.generated))

    override suspend fun countries(): List<CountryRecord> =
        dao.countries().map { CountryRecord(it.mcc, it.rows, it.importedAtMs, it.generated) }

    override suspend fun removeCountry(mcc: Int) = dao.removeCountry(mcc)

    private fun CellEntity.toCell() =
        Cell(Radio.valueOf(radio), mcc, mnc, area, cellId, unit, LatLon(lat, lon), rangeM, samples)

    private fun Cell.toEntity(): CellEntity {
        val t = tileOf(pos)
        return CellEntity(radio.name, mcc, mnc, area, cellId, unit, pos.lat, pos.lon, rangeM, samples, t.x, t.y)
    }
}
