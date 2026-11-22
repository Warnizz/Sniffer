package io.github.warnizz.sniffer.towers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CountryDataState(
    val records: List<CountryRecord> = emptyList(),
    val index: CountryIndex? = null,
    val busyMcc: Int? = null,
    val progressRows: Int = 0,
    val error: String? = null,
)

/** Owns downloading, updating and removing per-country tower data. */
class CountryDataManager(
    private val source: CountrySource,
    private val store: TowerStore,
    private val isMetered: () -> Boolean,
    private val lastCheckMs: suspend () -> Long?,
    private val setLastCheckMs: suspend (Long) -> Unit,
    private val nowMs: () -> Long,
) {
    private val _state = MutableStateFlow(CountryDataState())
    val state: StateFlow<CountryDataState> = _state.asStateFlow()
    private val lock = Mutex()

    /** Loads the local records and, if reachable, the mirror's index. */
    suspend fun refresh() {
        val records = store.countries()
        val index = source.index()
        _state.update { it.copy(records = records, index = index ?: it.index) }
    }

    suspend fun download(mcc: Int) = lock.withLock {
        _state.update { it.copy(busyMcc = mcc, progressRows = 0, error = null) }
        val result = source.import(mcc) { rows -> _state.update { it.copy(progressRows = rows) } }
        val error = when (result) {
            is ImportResult.Ok -> null
            ImportResult.NotAvailable -> "No tower data for MCC $mcc in the mirror yet"
            is ImportResult.Failed -> result.reason
        }
        _state.update { it.copy(records = store.countries(), busyMcc = null, error = error) }
    }

    suspend fun remove(mcc: Int) = lock.withLock {
        store.removeCountry(mcc)
        _state.update { it.copy(records = store.countries()) }
    }

    /** Weekly, unmetered-only check that re-downloads countries with a newer export. */
    suspend fun autoUpdate() {
        if (isMetered()) return
        val last = lastCheckMs()
        if (last != null && nowMs() - last < WEEK_MS) return
        setLastCheckMs(nowMs())
        for (rec in store.countries()) {
            if (source.updateAvailable(rec)) download(rec.mcc)
        }
    }

    /** User-triggered: refresh the index and re-download any country with a newer export. */
    suspend fun checkUpdatesNow() {
        refresh()
        for (rec in store.countries()) {
            if (source.updateAvailable(rec)) download(rec.mcc)
        }
        setLastCheckMs(nowMs())
    }

    private companion object { const val WEEK_MS = 7L * 24 * 3600 * 1000 }
}
