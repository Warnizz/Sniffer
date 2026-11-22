package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.geo.TileId

interface TowerStore {
    suspend fun cellsInTiles(tiles: List<TileId>): List<Cell>
    suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long)
    suspend fun tileFetchedAt(tile: TileId): Long?
}

class RoomTowerStore(db: TowerDb) : TowerStore {
    private val dao = db.dao()

    override suspend fun cellsInTiles(tiles: List<TileId>): List<Cell> =
        tiles.flatMap { t -> dao.cellsInTile(t.x, t.y).map { it.toCell() } }

    override suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long) =
        dao.replaceTile(TileEntity(tile.x, tile.y, fetchedAtMs), cells.map { it.toEntity(tile) })

    override suspend fun tileFetchedAt(tile: TileId): Long? = dao.tileFetchedAt(tile.x, tile.y)

    private fun CellEntity.toCell() =
        Cell(Radio.valueOf(radio), mcc, mnc, area, cellId, unit, LatLon(lat, lon), rangeM, samples)

    private fun Cell.toEntity(t: TileId) =
        CellEntity(radio.name, mcc, mnc, area, cellId, unit, pos.lat, pos.lon, rangeM, samples, t.x, t.y)
}
