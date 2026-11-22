package io.github.warnizz.sniffer

import android.app.Application
import androidx.room.Room
import io.github.warnizz.sniffer.engine.AndroidLocationSource
import io.github.warnizz.sniffer.engine.SnifferEngine
import io.github.warnizz.sniffer.live.CellScanner
import io.github.warnizz.sniffer.settings.SettingsStore
import io.github.warnizz.sniffer.sound.PdSoundSink
import io.github.warnizz.sniffer.towers.OpenCellIdClient
import io.github.warnizz.sniffer.towers.RoomTowerStore
import io.github.warnizz.sniffer.towers.TowerDb
import io.github.warnizz.sniffer.towers.TowerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SnifferApp : Application() {
    lateinit var engine: SnifferEngine
        private set
    lateinit var settingsStore: SettingsStore
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        val db = Room.databaseBuilder(this, TowerDb::class.java, "towers.db").fallbackToDestructiveMigration().build()
        val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val client = OpenCellIdClient(http)
        val repo = TowerRepository(RoomTowerStore(db), client::fetchTile, System::currentTimeMillis)
        val scanner = CellScanner(this)
        engine = SnifferEngine(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)),
            location = AndroidLocationSource(this),
            towers = repo::sitesNear,
            live = scanner::snapshots,
            sink = PdSoundSink(this),
            settings = settingsStore.settings,
            nowMs = System::currentTimeMillis,
        )
    }
}
