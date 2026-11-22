package io.github.snifferapp.sniffer.towers

import io.github.snifferapp.sniffer.geo.LatLon
import io.github.snifferapp.sniffer.geo.TileId
import io.github.snifferapp.sniffer.geo.distanceM
import io.github.snifferapp.sniffer.geo.tilesCovering

enum class DbStatus { OK, NO_KEY, INVALID_KEY, RATE_LIMITED, OFFLINE_PARTIAL }

data class TowersResult(val sites: List<TowerSite>, val status: DbStatus, val hasData: Boolean)

/**
 * Serves tower sites near a point from the local cache, fetching missing or stale tiles.
 * Tiles are fresh for 30 days. Failed tiles, and key/limit errors, back off for 10 minutes.
 */
class TowerRepository(
    private val store: TowerStore,
    private val fetch: suspend (key: String, TileId) -> FetchResult,
    private val nowMs: () -> Long,
) {
    private val failedAt = mutableMapOf<TileId, Long>()
    private var blocked: Triple<DbStatus, String, Long>? = null // status, key, since

    suspend fun sitesNear(center: LatLon, radiusM: Double, apiKey: String?): TowersResult {
        val tiles = tilesCovering(center, radiusM)
        val status = refresh(tiles, apiKey?.trim().orEmpty())
        val hasData = tiles.any { store.tileFetchedAt(it) != null }
        val cells = store.cellsInTiles(tiles).filter { distanceM(center, it.pos) <= radiusM }
        return TowersResult(clusterSites(cells), status, hasData)
    }

    private suspend fun refresh(tiles: List<TileId>, key: String): DbStatus {
        if (key.isEmpty()) return DbStatus.NO_KEY
        val now = nowMs()
        blocked?.let { (st, k, since) ->
            val stillBlocked = k == key && (st == DbStatus.INVALID_KEY || now - since < BACKOFF_MS)
            if (stillBlocked) return st
            blocked = null
        }
        var status = DbStatus.OK
        for (t in tiles) {
            val fetched = store.tileFetchedAt(t)
            if (fetched != null && now - fetched < FRESH_MS) continue
            val failed = failedAt[t]
            if (failed != null && now - failed < BACKOFF_MS) { status = DbStatus.OFFLINE_PARTIAL; continue }
            when (val r = fetch(key, t)) {
                is FetchResult.Ok -> { store.putTile(t, r.cells, now); failedAt.remove(t) }
                FetchResult.InvalidKey -> { blocked = Triple(DbStatus.INVALID_KEY, key, now); return DbStatus.INVALID_KEY }
                FetchResult.RateLimited -> { blocked = Triple(DbStatus.RATE_LIMITED, key, now); return DbStatus.RATE_LIMITED }
                is FetchResult.Failed -> { failedAt[t] = now; status = DbStatus.OFFLINE_PARTIAL }
            }
        }
        return status
    }

    private companion object {
        const val FRESH_MS = 30L * 24 * 3600 * 1000
        const val BACKOFF_MS = 10L * 60 * 1000
    }
}
