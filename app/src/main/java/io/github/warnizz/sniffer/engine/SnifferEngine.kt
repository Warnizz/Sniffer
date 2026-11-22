package io.github.warnizz.sniffer.engine

import io.github.warnizz.sniffer.geo.LatLon
import io.github.warnizz.sniffer.geo.distanceM
import io.github.warnizz.sniffer.live.HandoverDetector
import io.github.warnizz.sniffer.live.LiveSnapshot
import io.github.warnizz.sniffer.live.Match
import io.github.warnizz.sniffer.live.matchLive
import io.github.warnizz.sniffer.settings.Settings
import io.github.warnizz.sniffer.sound.ControlMapper
import io.github.warnizz.sniffer.sound.SoundControls
import io.github.warnizz.sniffer.sound.SoundSink
import io.github.warnizz.sniffer.towers.DbStatus
import io.github.warnizz.sniffer.towers.TowerSite
import io.github.warnizz.sniffer.towers.TowersResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Mode { OFF, AR, AMBIENT, AR_AND_AMBIENT }

data class SnifferState(
    val mode: Mode = Mode.OFF,
    val user: LatLon? = null,
    val sites: List<TowerSite> = emptyList(),
    val live: LiveSnapshot? = null,
    /** site.id → match, for every live cell that could be placed. */
    val matches: Map<String, Match> = emptyMap(),
    val servingSiteId: String? = null,
    val ownMccMnc: Pair<Int, Int>? = null,
    val dbStatus: DbStatus = DbStatus.WAITING,
    val controls: SoundControls? = null,
)

/**
 * Combines location, tower database and live radio data into sound controls once per second.
 * [scope] must be single-threaded (e.g. Dispatchers.Default.limitedParallelism(1)): run state is
 * kept in plain locals shared by the child coroutines.
 */
class SnifferEngine(
    private val scope: CoroutineScope,
    private val location: LocationSource,
    private val towers: suspend (center: LatLon, radiusM: Double, apiKey: String?) -> TowersResult,
    private val live: (intervalMs: Long) -> Flow<LiveSnapshot>,
    private val sink: SoundSink,
    private val settings: Flow<Settings>,
    private val nowMs: () -> Long,
) {
    private val _state = MutableStateFlow(SnifferState())
    val state: StateFlow<SnifferState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var ar = false
    @Volatile private var ambient = false
    private val detector = HandoverDetector()
    private val mapper = ControlMapper()

    // Callers may be on any thread; the mode change itself runs on the engine's single-threaded scope,
    // so it never interleaves with a tick.
    fun setAr(on: Boolean) { ar = on; scope.launch { apply() } }
    fun setAmbient(on: Boolean) { ambient = on; scope.launch { apply() } }

    fun setMode(mode: Mode) {
        ar = mode == Mode.AR || mode == Mode.AR_AND_AMBIENT
        ambient = mode == Mode.AMBIENT || mode == Mode.AR_AND_AMBIENT
        scope.launch { apply() }
    }

    private fun apply() {
        val mode = when {
            ar && ambient -> Mode.AR_AND_AMBIENT
            ar -> Mode.AR
            ambient -> Mode.AMBIENT
            else -> Mode.OFF
        }
        if (mode == _state.value.mode && (job != null) == (mode != Mode.OFF)) return
        job?.cancel()
        job = null
        _state.value = _state.value.copy(mode = mode)
        if (mode == Mode.OFF) { sink.stop(); return }
        sink.start()
        job = scope.launch { run(mode) }
    }

    private suspend fun run(mode: Mode) = coroutineScope {
        val arOn = mode != Mode.AMBIENT
        val ambientOn = mode != Mode.AR
        var cfg = Settings()
        var user: LatLon? = null
        var snap: LiveSnapshot = LiveSnapshot.EMPTY
        var lastProcessed: LiveSnapshot? = null
        var result = TowersResult(_state.value.sites, _state.value.dbStatus, _state.value.sites.isNotEmpty())
        var lastQuery: Triple<LatLon, Long, Settings>? = null

        launch { settings.collect { cfg = it } }
        launch { location.locations(if (arOn) 1_000 else 5_000).collect { user = it } }
        launch { live(if (arOn) 5_000 else 10_000).collect { snap = it } }
        launch {
            while (true) {
                val u = user
                val q = lastQuery
                val due = u != null && (q == null || distanceM(q.first, u) > REQUERY_M ||
                    nowMs() - q.second >= REQUERY_MS || q.third.apiKey != cfg.apiKey || q.third.radiusM != cfg.radiusM)
                if (u != null && due) {
                    lastQuery = Triple(u, nowMs(), cfg)
                    result = towers(u, cfg.radiusM.toDouble(), cfg.apiKey)
                }
                delay(TICK_MS)
            }
        }
        while (true) {
            delay(TICK_MS)
            val s = snap
            val fresh = s !== lastProcessed
            lastProcessed = s
            val handover = fresh && detector.onSnapshot(s)
            val newNeighbours = if (fresh) detector.newNeighbours(s) else 0
            val master = when {
                ambientOn -> cfg.ambientVolume
                cfg.arMuted -> 0f
                else -> cfg.arVolume
            }
            val u = user
            ensureActive()
            val controls = mapper.map(result.sites, result.hasData, u, cfg.radiusM.toDouble(), s, handover, newNeighbours, master, nowMs())
            sink.send(controls)
            val liveCells = listOfNotNull(s.serving) + s.neighbours
            val matches = liveCells.mapNotNull { c -> matchLive(c, result.sites, u)?.let { c to it } }
            _state.value = SnifferState(
                mode = mode,
                user = u,
                sites = result.sites,
                live = s,
                matches = matches.associate { (_, m) -> m.site.id to m },
                servingSiteId = matches.firstOrNull { it.first.serving }?.second?.site?.id,
                ownMccMnc = s.serving?.let { c -> if (c.mcc != null && c.mnc != null) c.mcc to c.mnc else null },
                dbStatus = result.status,
                controls = controls,
            )
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val REQUERY_M = 300.0
        const val REQUERY_MS = 60_000L
    }
}
