package io.github.warnizz.sniffer.towers

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "cells",
    primaryKeys = ["radio", "mcc", "mnc", "area", "cellId"],
    indices = [Index("tileX", "tileY")],
)
data class CellEntity(
    val radio: String, val mcc: Int, val mnc: Int, val area: Int, val cellId: Long, val unit: Int?,
    val lat: Double, val lon: Double, val rangeM: Int, val samples: Int, val tileX: Int, val tileY: Int,
)

@Entity(tableName = "tiles", primaryKeys = ["x", "y"])
data class TileEntity(val x: Int, val y: Int, val fetchedAtMs: Long)

@Entity(tableName = "countries")
data class CountryEntity(@androidx.room.PrimaryKey val mcc: Int, val rows: Int, val importedAtMs: Long, val generated: String)

@Dao
interface TowerDao {
    @Query("SELECT * FROM cells WHERE tileX = :x AND tileY = :y")
    suspend fun cellsInTile(x: Int, y: Int): List<CellEntity>

    @Query("SELECT fetchedAtMs FROM tiles WHERE x = :x AND y = :y")
    suspend fun tileFetchedAt(x: Int, y: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: TileEntity)

    /** Upserts only: refreshing a tile from the API never erases imported cells. */
    @Transaction
    suspend fun putTile(tile: TileEntity, cells: List<CellEntity>) {
        insertCells(cells)
        insertTile(tile)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCountry(country: CountryEntity)

    @Query("SELECT * FROM countries ORDER BY mcc")
    suspend fun countries(): List<CountryEntity>

    @Query("DELETE FROM cells WHERE mcc = :mcc")
    suspend fun deleteCountryCells(mcc: Int)

    @Query("DELETE FROM countries WHERE mcc = :mcc")
    suspend fun deleteCountry(mcc: Int)

    @Transaction
    suspend fun removeCountry(mcc: Int) {
        deleteCountryCells(mcc)
        deleteCountry(mcc)
    }
}

@Database(entities = [CellEntity::class, TileEntity::class, CountryEntity::class], version = 2, exportSchema = false)
abstract class TowerDb : RoomDatabase() {
    abstract fun dao(): TowerDao
}
