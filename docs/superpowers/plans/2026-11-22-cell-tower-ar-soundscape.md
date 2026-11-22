# Sniffer — Cell Tower AR & Ambient Soundscape Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An F-Droid-ready Android app that shows nearby cell towers as camera-overlay beacons and plays a Pure Data soundscape driven by tower density plus live radio data, also with the screen off.

**Architecture:** One `app` module, split by responsibility into packages. Pure Kotlin logic (geo math, density, smoothing, handovers, AR projection) is unit tested on the JVM. One process-wide `SnifferEngine` combines location, OpenCelliD towers (cached in Room) and live `TelephonyManager` data into `SoundControls`, which go to a libpd `SoundSink`. The UI reads its `StateFlow`. A foreground service keeps the engine alive for ambient mode.

**Tech Stack:** Kotlin, Gradle (version catalog), Jetpack Compose, CameraX, Room, DataStore, OkHttp, kotlinx.serialization, coroutines, libpd via pd-for-android (git submodule, built from source with the NDK), JUnit4, kotlinx-coroutines-test, Robolectric (Room test only).

**Spec:** `docs/superpowers/specs/2026-11-22-cell-tower-ar-soundscape-design.md`

## Global Constraints

- No Google Play Services, Firebase, ARCore or any proprietary dependency. Every dependency must be FOSS and buildable by F-Droid.
- Licence: GPL-3.0-or-later (`LICENSE` at repo root, SPDX header not required per file).
- minSdk 29. compileSdk/targetSdk = latest stable SDK installed in Task 1.
- Application ID / package: `io.github.snifferapp.sniffer` (placeholder. Change before the first F-Droid submission, because it can't change afterwards).
- No API key in source, resources or build config. The OpenCelliD key comes only from user settings.
- Network traffic goes only to `opencellid.org`, only when a key is set.
- The Pd patch uses **Pd vanilla objects only** (libpd has no externals).
- App → Pd receive names are fixed: `density` 0–1, `nearest` metres, `signal` 0–1, `nettype` 0/2/3/4/5 (0 = no service), `neighbours` count, `handover` bang, `neighbour_event` bang, `master` 0–1.
- Assumed mast height 30 m, assumed phone height 1.5 m, flat ground.
- Density radius default 2000 m (settings: 1000 / 2000 / 3000).
- OpenCelliD attribution text visible in-app: "Tower data © OpenCelliD contributors, CC BY-SA 4.0".

## Review Focus

1. **No GPS fix yet / location denied:** the app doesn't crash. AR shows "Waiting for GPS", and sound runs on the live-only fallback. Pinned in Task 7 (`mapper_noLocation_usesLiveFallback`) and Task 8 (`engine_noLocation_stillEmitsControls`).
2. **OpenCelliD failure (bad key, rate limit, offline, HTTP 5xx):** cached towers are kept, the error shows in state, and there's no retry storm (a failed tile isn't retried for 10 min). Pinned in Task 5 (`repository_fetchFails_keepsCacheAndBacksOff`, `repository_invalidKey_reportsKeyError`).
3. **Handover ping-pong at cell edges:** handover bangs are capped at 1 per 10 s. Pinned in Task 6 (`handover_pingPong_rateLimited`).
4. **No SIM / airplane mode / empty or null `getAllCellInfo`:** `nettype` 0, `signal` 0.5 (neutral, so no constant max crackle), `neighbours` 0. Pinned in Task 6 (`snapshot_empty_isNeutral`).
5. **Junk DB rows and degenerate geometry:** rows at (0,0), out-of-range lat/lon, duplicates, and a tower closer than 5 m get filtered or clamped. A tower behind the camera never draws. Pinned in Task 4 (`parse_dropsJunkRows`) and Task 10 (`project_behindCamera_null`, `project_veryCloseTower_noNaN`).

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, LICENSE, README.md
external/pd-for-android/                (git submodule, recursive; provides :PdCore)
pd/sniffer.pd                           main soundscape patch (also copied to app assets)
pd/harness.pd                           desktop test harness: sliders/bangs → same receive names
app/src/main/assets/sniffer.pd          copy of pd/sniffer.pd (Gradle copy task, not hand-edited)
app/src/main/java/io/github/snifferapp/sniffer/
  SnifferApp.kt                         Application; owns SnifferEngine singleton
  geo/Geo.kt                            distance, bearing, elevation, LatLon
  geo/Tiles.kt                          fixed tile grid for OpenCelliD queries
  towers/Tower.kt                       Cell, TowerSite, Radio models
  towers/OpenCellIdClient.kt            HTTP + JSON parse + row filtering
  towers/SiteClusterer.kt               cells → sites
  towers/TowerDb.kt                     Room entities/DAO/database
  towers/TowerStore.kt                  interface + Room impl
  towers/TowerRepository.kt             tile fetch, cache, expiry, backoff
  live/LiveCell.kt                      LiveCell, LiveSnapshot, NetType
  live/SignalNormalizer.kt
  live/HandoverDetector.kt
  live/NeighbourMatcher.kt
  live/CellScanner.kt                   Android TelephonyManager → Flow<LiveSnapshot>
  sound/SoundControls.kt                data class + SoundSink interface
  sound/DensityCalculator.kt
  sound/Smoother.kt
  sound/ControlMapper.kt
  sound/PdSoundSink.kt                  libpd impl of SoundSink
  engine/LocationSource.kt              interface + LocationManager impl
  engine/SnifferEngine.kt               combines everything, exposes StateFlow<SnifferState>
  engine/SnifferService.kt              foreground service (location|mediaPlayback)
  settings/SettingsStore.kt             DataStore wrapper
  ar/ArProjector.kt                     pure projection math
  ar/OrientationSource.kt               rotation-vector sensor → Flow<FloatArray>
  ar/ArScreen.kt                        CameraX preview + beacon overlay + details sheet
  ui/MainActivity.kt, ui/HomeScreen.kt, ui/SettingsScreen.kt, ui/Permissions.kt
app/src/test/java/io/github/snifferapp/sniffer/...   JVM tests mirroring the packages
fastlane/metadata/android/en-US/        F-Droid listing
```

---

### Task 1: Toolchain and project skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/.../ui/MainActivity.kt`, `app/src/main/java/.../SnifferApp.kt`, `LICENSE`, `README.md`, `.gitignore`, Gradle wrapper
- Test: `app/src/test/java/.../SmokeTest.kt`

**Interfaces:**
- Produces: buildable `:app` module with Compose; `SnifferApp : Application`.

- [ ] **Step 1: Install toolchain** (macOS): `brew install --cask temurin@17 android-commandlinetools pd`. Then `sdkmanager "platform-tools" "platforms;android-<latest>" "build-tools;<latest>" "ndk;<latest LTS>" "cmake;3.22.1"`, and accept licences. Export `ANDROID_HOME`, and write `sdk.dir` to `local.properties` (git-ignored).
- [ ] **Step 2: Create the Gradle project** with the version catalog, the Compose app module, and a `MainActivity` showing the text "Sniffer". Generate the wrapper with `gradle wrapper` (or copy one from an Android Studio template). Add the GPL-3.0 `LICENSE` text.
- [ ] **Step 3: Write `SmokeTest.smoke()`**, which asserts `1 + 1 == 2`. It only proves the JVM test task runs.
- [ ] **Step 4: Verify:** `./gradlew assembleDebug testDebugUnitTest`. Expected: BUILD SUCCESSFUL, and the APK at `app/build/outputs/apk/debug/`.
- [ ] **Step 5: Commit** `chore: android project skeleton`.

### Task 2: libpd integration with a test tone

**Files:**
- Create: `external/pd-for-android` (submodule), `pd/sniffer.pd` (temporary: `[r master]` → `[line~]` gain on a 110 Hz `[osc~]` → `[dac~]`), `sound/SoundControls.kt`, `sound/PdSoundSink.kt`
- Modify: `settings.gradle.kts` (include `:PdCore` from the submodule), `app/build.gradle.kts` (dependency + copy task `pd/*.pd → src/main/assets/`), `MainActivity.kt` (temporary "Play tone" toggle)

**Interfaces:**
- Produces:
  ```kotlin
  data class SoundControls(
      val density: Float, val nearestM: Float, val signal: Float, val netType: Int,
      val neighbours: Int, val handover: Boolean, val neighbourEvent: Boolean, val master: Float)
  interface SoundSink { fun start(); fun stop(); fun send(c: SoundControls) }
  class PdSoundSink(context: Context) : SoundSink
  ```
  `send` does `PdBase.sendFloat` for each float field, and `PdBase.sendBang("handover")` / `sendBang("neighbour_event")` only when the flag is true.

- [ ] **Step 1:** `git submodule add https://github.com/libpd/pd-for-android external/pd-for-android && git submodule update --init --recursive`. Then include its `PdCore` project in Gradle. Follow the submodule's own README for the NDK build, and pin the submodule commit.
- [ ] **Step 2:** Implement `PdSoundSink`: copy the asset `sniffer.pd` to `filesDir`, then `PdAudio.initAudio(AudioParameters.suggestSampleRate(), 0, 2, 8, true)`, `PdBase.openPatch`, and `PdAudio.startAudio(context)`. `stop()` calls `PdAudio.stopAudio()`.
- [ ] **Step 3: Verify the patch loads headless:** `pd -nogui -noaudio -stderr -send "pd quit" -open pd/sniffer.pd 2>&1 | grep -c "couldn't create"`. Expected: `0`.
- [ ] **Step 4: Verify the build:** `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL, and the APK contains `lib/*/libpd.so` (check with `unzip -l`).
- [ ] **Step 5: Device check** (needs a phone over adb): `./gradlew installDebug`, tap "Play tone", and hear the 110 Hz tone.
- [ ] **Step 6: Commit** `feat: libpd audio with test tone`.

### Task 3: Geo math and tile grid

**Files:**
- Create: `geo/Geo.kt`, `geo/Tiles.kt`
- Test: `geo/GeoTest.kt`, `geo/TilesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class LatLon(val lat: Double, val lon: Double)
  fun distanceM(a: LatLon, b: LatLon): Double          // haversine, R = 6_371_000
  fun bearingDeg(from: LatLon, to: LatLon): Double     // 0..360, 0 = north
  fun elevationDeg(distanceM: Double, mastM: Double = 30.0, eyeM: Double = 1.5): Double // atan2(mast-eye, max(distance, 5.0))
  data class TileId(val x: Int, val y: Int)            // x = floor(lon/0.015), y = floor(lat/0.015)
  fun tileOf(p: LatLon): TileId
  fun tileBbox(t: TileId): DoubleArray                 // [latMin, lonMin, latMax, lonMax]
  fun tilesCovering(center: LatLon, radiusM: Double): List<TileId>
  ```
  The tile size is 0.015°, which keeps each query under OpenCelliD's 4 km² bbox limit at any latitude.

- [ ] **Step 1: Write tests.** `distance_copenhagenToMalmo` (55.6761,12.5683 → 55.6050,13.0038) ≈ 28_000 m ±500. `bearing_dueEast_is90` (0,0 → 0,1) = 90 ±0.01. `bearing_dueNorth_is0`. `elevation_100m` = atan2(28.5,100) ≈ 15.9° ±0.1. `elevation_zeroDistance_clampedNoNaN`: it's finite and ≈ 80°. `tilesCovering_2km_containsCenterTile` and `tilesCovering_2km_size` gives between 9 and 30 tiles at lat 55.6. `tileBbox_areaUnder4km2_atEquatorAndLat60`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*geo*'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** both files.
- [ ] **Step 4: Run** again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: geo math and tile grid`.

### Task 4: Tower models, OpenCelliD client, site clustering

**Files:**
- Create: `towers/Tower.kt`, `towers/OpenCellIdClient.kt`, `towers/SiteClusterer.kt`
- Test: `towers/OpenCellIdParseTest.kt`, `towers/SiteClustererTest.kt`, `app/src/test/resources/opencellid_area.json` (hand-written fixture in the API's JSON shape)

**Interfaces:**
- Consumes: `LatLon`, `TileId`, `tileBbox`, `distanceM`.
- Produces:
  ```kotlin
  enum class Radio { GSM, UMTS, LTE, NR, CDMA, UNKNOWN }
  data class Cell(val radio: Radio, val mcc: Int, val mnc: Int, val area: Int, val cellId: Long,
                  val unit: Int?,  /* PSC/PCI */ val pos: LatLon, val rangeM: Int, val samples: Int)
  data class TowerSite(val id: String, val mcc: Int, val mnc: Int, val pos: LatLon, val cells: List<Cell>)
  sealed interface FetchResult { data class Ok(val cells: List<Cell>) : FetchResult
      object InvalidKey : FetchResult; object RateLimited : FetchResult; data class Failed(val reason: String) : FetchResult }
  class OpenCellIdClient(http: OkHttpClient, baseUrl: String = "https://opencellid.org") {
      suspend fun fetchTile(key: String, tile: TileId): FetchResult }
  fun parseArea(json: String): List<Cell>               // internal, tested directly
  fun clusterSites(cells: List<Cell>, mergeM: Double = 50.0): List<TowerSite>
  ```
  The request is `GET /cell/getInArea?key=…&BBOX=latMin,lonMin,latMax,lonMax&format=json&limit=1000`. Page with `offset` while a page returns a full `limit`. Check the field names and the error codes/messages for a bad key and the rate limit against the live API docs before writing the fixture. The fixture must match them.
  `clusterSites` puts cells into the same site only if they have the same mcc+mnc and lie within `mergeM` of the site's first cell. The site position is the mean of its cells. `id` = `"$mcc-$mnc-${first.area}-${first.cellId}"`.

- [ ] **Step 1: Write tests.** `parse_readsCells`: the fixture's 5 valid rows give 5 `Cell`s, with the right radio, mcc, mnc and pos. `parse_dropsJunkRows`: rows with lat=0&&lon=0, |lat|>90, |lon|>180, or a duplicate (radio, mcc, mnc, area, cellId) are dropped. `parse_unknownRadio_mapsToUnknown`. `cluster_sameCarrierWithin50m_merges` (3 cells → 1 site). `cluster_differentCarrierSameSpot_separate`. `cluster_sameCarrier200mApart_separate`.
- [ ] **Step 2: Run** `--tests '*towers*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the models, `parseArea` (kotlinx.serialization with `ignoreUnknownKeys`), `fetchTile`, and `clusterSites`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: OpenCelliD client and site clustering`.

### Task 5: Tower cache and repository

**Files:**
- Create: `towers/TowerDb.kt`, `towers/TowerStore.kt`, `towers/TowerRepository.kt`
- Test: `towers/TowerRepositoryTest.kt` (in-memory fake `TowerStore`, fake fetcher), `towers/RoomTowerStoreTest.kt` (Robolectric, in-memory Room)

**Interfaces:**
- Consumes: `Cell`, `TileId`, `tilesCovering`, `FetchResult`, `clusterSites`, `distanceM`.
- Produces:
  ```kotlin
  interface TowerStore {
      suspend fun cellsInTiles(tiles: List<TileId>): List<Cell>
      suspend fun putTile(tile: TileId, cells: List<Cell>, fetchedAtMs: Long)
      suspend fun tileFetchedAt(tile: TileId): Long? }
  class RoomTowerStore(db: TowerDb) : TowerStore
  enum class DbStatus { OK, NO_KEY, INVALID_KEY, RATE_LIMITED, OFFLINE_PARTIAL }
  data class TowersResult(val sites: List<TowerSite>, val status: DbStatus, val hasData: Boolean)
  class TowerRepository(store: TowerStore, fetch: suspend (key: String, TileId) -> FetchResult,
                        nowMs: () -> Long) {
      suspend fun sitesNear(center: LatLon, radiusM: Double, apiKey: String?): TowersResult }
  ```
  Rules: a tile is fresh for 30 days. A failed tile isn't retried for 10 min (kept in memory, keyed by tile). `InvalidKey` / `RateLimited` stop fetching the remaining tiles in that call. The result only includes sites within `radiusM`. `hasData` = at least one covering tile was ever fetched. With no key, nothing is fetched, and status is `NO_KEY`.

- [ ] **Step 1: Write tests.** `repository_freshTile_noFetch`. `repository_staleTile_refetches` (31 days). `repository_noKey_returnsCachedOnly_statusNoKey`. `repository_fetchFails_keepsCacheAndBacksOff`: a second call within 10 min doesn't call fetch, and a call at 11 min does. `repository_invalidKey_reportsKeyError`, which also stops after the first tile. `repository_filtersToRadius`. `room_putThenRead_roundTrips`.
- [ ] **Step 2: Run** the tests. Expected: FAIL.
- [ ] **Step 3: Implement.** Room entities: `CellEntity` (primary key radio, mcc, mnc, area, cellId; plus a `tileX`/`tileY` index) and `TileEntity(x, y, fetchedAtMs)`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: tower cache and repository`.

### Task 6: Live cell layer

**Files:**
- Create: `live/LiveCell.kt`, `live/SignalNormalizer.kt`, `live/HandoverDetector.kt`, `live/NeighbourMatcher.kt`, `live/CellScanner.kt`
- Test: `live/SignalNormalizerTest.kt`, `live/HandoverDetectorTest.kt`, `live/NeighbourMatcherTest.kt`, `live/LiveSnapshotTest.kt`

**Interfaces:**
- Consumes: `Radio`, `TowerSite`, `Cell`, `distanceM`.
- Produces:
  ```kotlin
  data class LiveCell(val radio: Radio, val mcc: Int?, val mnc: Int?, val area: Int?, val cellId: Long?,
                      val pci: Int?, val dbm: Int?, val serving: Boolean)
  data class LiveSnapshot(val serving: LiveCell?, val neighbours: List<LiveCell>, val netType: Int /*0,2,3,4,5*/,
                          val timeMs: Long) {
      val signal: Float  // SignalNormalizer on serving, or 0.5f if serving == null
  }
  object SignalNormalizer { fun normalize(radio: Radio, dbm: Int?): Float }  // clamp 0..1; null → 0.5
  class HandoverDetector(minIntervalMs: Long = 10_000) {
      fun onSnapshot(s: LiveSnapshot): Boolean           // true = emit handover bang
      fun newNeighbours(s: LiveSnapshot): Int }          // count of neighbour keys not in previous snapshot
  data class Match(val site: TowerSite, val probable: Boolean)
  fun matchLive(cell: LiveCell, sites: List<TowerSite>, user: LatLon?): Match?
  class CellScanner(context: Context) { fun snapshots(intervalMs: Long): Flow<LiveSnapshot> }
  ```
  Normalisation ranges (dBm → 0..1, linear): LTE/NR −140..−44, UMTS −120..−24, GSM/CDMA −113..−51.
  Handover = the serving key (radio, mcc, mnc, area, cellId) changes from one non-null value to a different non-null value, rate-limited by `minIntervalMs`. The first snapshot never fires.
  `matchLive`: an exact match on (mcc, mnc, area, cellId) → `probable = false`. Otherwise, if `pci` is set, take the nearest site to `user` within 3 km that has a cell with the same mcc/mnc (use the serving cell's mcc/mnc when the neighbour's is null), radio and `unit == pci` → `probable = true`. Otherwise null.
  `CellScanner`: uses the default data subscription (`SubscriptionManager.getDefaultDataSubscriptionId()` → `createForSubscriptionId`). Every `intervalMs` it calls `requestCellInfoUpdate`. It maps `CellInfoLte/Nr/Wcdma/Gsm/Cdma` to `LiveCell`, where `Int.MAX_VALUE` / `CellInfo.UNAVAILABLE` → null. `netType` comes from the serving radio, upgraded to 5 when the `TelephonyDisplayInfo` override is NR_NSA / NR_ADVANCED. That requires the optional READ_PHONE_STATE permission; skip the upgrade silently without it. A `SecurityException` or a null list → empty snapshot.

- [ ] **Step 1: Write tests.** `normalize_lte_bounds` (−140→0, −44→1, −92→0.5). `normalize_null_isNeutral`. `normalize_outOfRange_clamped`. `handover_firstSnapshot_false`. `handover_change_true`. `handover_pingPong_rateLimited`: A→B at 0 s is true, B→A at 3 s is false, A→B at 12 s is true. `handover_toNull_false`. `newNeighbours_countsOnlyNew`. `match_exact_notProbable`. `match_pci_nearest_probable`. `match_pci_over3km_null`. `snapshot_empty_isNeutral`: serving null gives signal 0.5, netType 0, neighbours empty.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the pure classes, then `CellScanner`. `CellScanner` gets no unit test, and is verified on the device in Task 8.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: live cell layer`.

### Task 7: Density, smoothing and control mapping

**Files:**
- Create: `sound/DensityCalculator.kt`, `sound/Smoother.kt`, `sound/ControlMapper.kt`
- Test: `sound/DensityCalculatorTest.kt`, `sound/SmootherTest.kt`, `sound/ControlMapperTest.kt`

**Interfaces:**
- Consumes: `TowerSite`, `LatLon`, `distanceM`, `LiveSnapshot`, `SoundControls`.
- Produces:
  ```kotlin
  object DensityCalculator {
      fun raw(sites: List<TowerSite>, user: LatLon, radiusM: Double): Double // Σ 1/(1+(d/250)²), d ≤ radiusM
      fun liveRaw(s: LiveSnapshot): Double                                   // 0.8·neighbours + 1.0·(serving != null)
      fun normalize(raw: Double): Float }                                    // 1 − exp(−raw/8)
  class Smoother(tauMs: Double = 20_000.0) { fun update(target: Float, nowMs: Long): Float } // exponential; first call returns target
  class ControlMapper(smoother: Smoother = Smoother()) {
      fun map(sites: List<TowerSite>, hasDbData: Boolean, user: LatLon?, radiusM: Double,
              live: LiveSnapshot, handover: Boolean, newNeighbours: Int, master: Float, nowMs: Long): SoundControls }
  ```
  `map` uses the DB raw density when `hasDbData && user != null`, otherwise `liveRaw`. The result is normalised and then smoothed. `nearestM` = the distance to the nearest site, or 5000 if there's none or no location. `neighbourEvent = newNeighbours > 0`.

- [ ] **Step 1: Write tests.** `raw_oneSiteAt0m_is1`. `raw_siteAt250m_isHalf`. `raw_ignoresBeyondRadius`. `normalize_zero_isZero`. `normalize_monotonic_andBelow1`. `smoother_firstValue_passthrough`. `smoother_stepChange_after20s_about63pct` (±2 %). `smoother_noLargeJump_in1s`: a 0→1 step moves less than 0.06 after 1 s. `mapper_noLocation_usesLiveFallback`. `mapper_withDb_usesDbDensity`. `mapper_nearest_noSites_is5000`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: density and control mapping`.

### Task 8: Settings, location, engine and foreground service

**Files:**
- Create: `settings/SettingsStore.kt`, `engine/LocationSource.kt`, `engine/SnifferEngine.kt`, `engine/SnifferService.kt`
- Modify: `SnifferApp.kt` (construct the engine), `AndroidManifest.xml` (permissions, service with `foregroundServiceType="location|mediaPlayback"`)
- Test: `engine/SnifferEngineTest.kt` (fakes for location, towers, live, sink, settings; `runTest` with virtual time)

**Interfaces:**
- Consumes: `TowerRepository`, `CellScanner`, `HandoverDetector`, `ControlMapper`, `SoundSink`, `matchLive`.
- Produces:
  ```kotlin
  data class Settings(val apiKey: String?, val radiusM: Int = 2000, val ambientVolume: Float = 0.8f,
                      val arVolume: Float = 0.3f, val arMuted: Boolean = false)
  class SettingsStore(context: Context) { val settings: Flow<Settings>; suspend fun update(f: (Settings) -> Settings) }
  interface LocationSource { fun locations(intervalMs: Long): Flow<LatLon?> } // emits null until first fix
  enum class Mode { OFF, AR, AMBIENT, AR_AND_AMBIENT }
  data class SnifferState(val user: LatLon?, val sites: List<TowerSite>, val live: LiveSnapshot?,
                          val matches: Map<String, Match> /* site.id → match for live cells */,
                          val ownMccMnc: Pair<Int, Int>?, val dbStatus: DbStatus, val controls: SoundControls?)
  class SnifferEngine(scope: CoroutineScope, location: LocationSource, towers: TowerRepository,
                      live: (Long) -> Flow<LiveSnapshot>, sink: SoundSink, settings: Flow<Settings>,
                      nowMs: () -> Long) {
      val state: StateFlow<SnifferState>
      fun setMode(mode: Mode) }
  ```
  Behaviour: `OFF` stops collection and calls `sink.stop()`. Any other mode starts the sink. Location interval: 1 s in AR, 5 s in ambient-only. Live interval: 5 s in AR, 10 s in ambient-only. Re-query towers when the user has moved more than 300 m since the last query, or every 60 s. A control tick runs every 1 s. `master` = `ambientVolume` when the mode includes ambient, otherwise `arVolume`, or 0 when `arMuted` in AR-only mode.
  `SnifferService`: `startForeground` with a notification ("Sniffer is listening to the network", Pause/Stop actions, and a "No tower data — add an OpenCelliD key" line when `dbStatus != OK`). It calls `engine.setMode` with AMBIENT or AR_AND_AMBIENT. Stop → the AR mode, or OFF.
  `LocationSource` impl: `LocationManager` with `GPS_PROVIDER` plus `NETWORK_PROVIDER` if present (it may be missing on de-Googled phones), and `PASSIVE_PROVIDER`. No Fused provider.

- [ ] **Step 1: Write tests.** `engine_off_sinkStopped`. `engine_ambient_sendsControlsEverySecond`. `engine_noLocation_stillEmitsControls` (live fallback). `engine_moved350m_requeriesTowers`. `engine_moved100m_noRequery`. `engine_arMuted_masterZero`. `engine_handover_sentOnce`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the engine, settings, location, service and manifest.
- [ ] **Step 4: Run.** Expected: PASS. Then run `./gradlew assembleDebug`, which should succeed.
- [ ] **Step 5: Device check:** start ambient from a temporary button and lock the screen. The test tone keeps playing, and `adb logcat -s SnifferEngine` shows controls once per second with plausible `signal`/`netType`.
- [ ] **Step 6: Commit** `feat: engine, settings and ambient service`.

### Task 9: The soundscape patch and desktop harness

**Files:**
- Create: `pd/sniffer.pd` (replaces the test tone), `pd/harness.pd`, `pd/README.md` (how to tune by ear)

**Interfaces:**
- Consumes: the fixed receive names from Global Constraints.
- Produces: an audio patch. Every input is smoothed inside the patch with `[line~]` / `[line]` (≥ 1500 ms for floats), and it ends `[clip~ -0.9 0.9]` → master `[*~]` → `[dac~]`.

Structure (vanilla objects only):
- **Drone (bed, from `density`):** 6 detuned saw voices (`[phasor~]`) on the root and fifth, plus an octave. Voice k's gain fades in as density passes k/6, so a forest gives 1 quiet voice and downtown gives 6. They go through `[vcf~]`, whose cutoff runs from 200 Hz to 2500 Hz with density, plus a slow LFO (0.05 Hz) on the cutoff.
- **Static (from `signal`, `handover`, `density`):** `[noise~]` → `[bp~ 3000 2]` hiss, plus crackle from sparse random impulses (`[noise~]` → `[expr~ $v1 > threshold]` → short `[vline~]` envelopes). Amount = 0.6·(1−signal) + 0.2·density. `handover` adds a 1.5 s burst that swells and fades. `nettype 0` mutes the crackle density to a sparse tick.
- **Leads (from `density`, `neighbour_event`, `handover`):** a monophonic `[phasor~]`→`[lop~]` voice with slow attack/release (≥ 400 ms). Notes come from a minor pentatonic on the drone root. A random `[metro]` plays a phrase with probability clamp((density−0.35)/0.5, 0, 1). `neighbour_event` and `handover` each trigger a 3–5 note phrase.
- **Colour (from `nettype`):** the root shifts per type (2G 55 Hz, 3G 65.4 Hz, 4G 73.4 Hz, 5G 82.4 Hz, 0 → keep the last). 2G also gets a pulse-ish timbre (`[phasor~]` → `[expr~]` square) and 5G brighter filtering, all crossfaded over 4 s.
- **Harness:** a slider per float receive, bang buttons for the two events, and a `[loadbang]` preset for "forest" / "suburb" / "downtown". It opens `sniffer.pd` as an abstraction or `[declare]` + direct include.

- [ ] **Step 1: Build the patch** in Pd (or write `.pd` text), section by section.
- [ ] **Step 2: Headless load check:** `pd -nogui -noaudio -stderr -send "pd quit" -open pd/sniffer.pd 2>&1 | grep -E -c "couldn't create|error"`. Expected: `0`. Repeat for `pd/harness.pd`.
- [ ] **Step 3: Render check:** add a `-batch` render script `pd/render-test.pd` that drives forest (density 0.05, signal 0.8) then downtown (density 0.9, signal 0.3) for 10 s each with `[writesf~]`. Check that the downtown RMS is above the forest RMS, and that no sample exceeds 0.9 (a short Python/`sox --stats` check).
- [ ] **Step 4: Listen** via the harness: forest is a quiet hum, downtown a dense crackle wall with leads, handover a clear swell, and nothing jumps abruptly.
- [ ] **Step 5:** `./gradlew assembleDebug` (the copy task puts the patch in assets). Device check in ambient mode.
- [ ] **Step 6: Commit** `feat: soundscape patch and harness`.

### Task 10: AR view

**Files:**
- Create: `ar/ArProjector.kt`, `ar/OrientationSource.kt`, `ar/ArScreen.kt`
- Test: `ar/ArProjectorTest.kt`

**Interfaces:**
- Consumes: `SnifferState`, `TowerSite`, `Match`, `bearingDeg`, `elevationDeg`, `distanceM`, `SettingsStore`.
- Produces:
  ```kotlin
  data class ScreenPoint(val x: Float, val y: Float)
  object ArProjector {
      /** r = row-major 3×3 device→world (ENU) matrix from SensorManager.getRotationMatrixFromVector. */
      fun project(r: FloatArray, bearingDeg: Double, elevationDeg: Double,
                  viewW: Int, viewH: Int, hFovDeg: Double): ScreenPoint? }
  class OrientationSource(context: Context) { fun rotation(): Flow<FloatArray> }
  @Composable fun ArScreen(engine: SnifferEngine, settings: SettingsStore)
  ```
  Projection algorithm (since the tests don't determine it on their own):
  ```
  w = (sin b·cos e, cos b·cos e, sin e)            // world ENU direction
  d = Rᵀ·w                                          // device coords
  if (d.z >= -1e-3) return null                     // behind / sideways to back camera
  f = (viewW/2) / tan(hFov/2)
  x = viewW/2 + f·(d.x / -d.z);  y = viewH/2 − f·(d.y / -d.z)
  return null if x or y outside [-0.1·size, 1.1·size]
  ```
  hFov comes from `CameraCharacteristics` (sensor width and focal length), with a 60° fallback.
  Overlay: draw at most 60 beacons, nearest first. Size and alpha scale with 1/distance. Own-carrier sites (`ownMccMnc`) are bright and others at 35 % alpha. Live-matched sites get a ring, and the serving cell a pulsing highlight. The label shows distance (m/km) and radio. Tapping opens a `ModalBottomSheet` with the cells' radio, MCC/MNC, LAC/TAC, cell ID, range, distance, live dBm if heard, "probable match" when `probable`, and the source line "OpenCelliD". The top bar has a mute toggle (writes `arMuted`), a DB status chip, and "Waiting for GPS" when `user == null`. On enter: `engine.setMode(AR or AR_AND_AMBIENT)`; on leave, return to the previous mode.

- [ ] **Step 1: Write tests,** using `R_uprightFacingNorth = [1,0,0, 0,0,-1, 0,1,0]` and a view of 1080×1920 with hFov 60. `project_ahead_center` (b 0, e 0 → 540, 960 ±1). `project_10degRight` (x = 540 + f·tan 10° ±1). `project_5degUp_aboveCenter` (y < 960). `project_behindCamera_null` (b 180). `project_outsideFov_null` (b 90). `project_veryCloseTower_noNaN` (e = elevationDeg(0.0) gives a finite value or null, never NaN).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the projector, orientation source (`TYPE_ROTATION_VECTOR`, `SENSOR_DELAY_GAME`, low-pass α 0.15 on the matrix), and the screen (CameraX `PreviewView` in `AndroidView` plus a Compose `Canvas` overlay).
- [ ] **Step 4: Run.** Expected: PASS. `./gradlew assembleDebug` should succeed.
- [ ] **Step 5: Device check** outdoors: beacons stay roughly put while you turn, a known tower appears in about the right direction, and tapping opens the details.
- [ ] **Step 6: Commit** `feat: AR camera view`.

### Task 11: Home, settings, permissions, attribution

**Files:**
- Create: `ui/HomeScreen.kt`, `ui/SettingsScreen.kt`, `ui/Permissions.kt`
- Modify: `ui/MainActivity.kt` (navigation: Home ↔ AR ↔ Settings; remove the temporary buttons)

**Interfaces:**
- Consumes: `SnifferEngine`, `SettingsStore`, `SnifferService`.
- Produces: the final UI.
  Home has big "Look around" (→ AR) and "Ambient" (start/stop service) buttons, live stats (nearby sites, serving cell, signal, network type), the DB status, and a "No OpenCelliD key — add one in settings to see towers" card when `NO_KEY` / `INVALID_KEY`.
  Settings: the API key field with a link to opencellid.org to get one, radius (1/2/3 km), ambient volume, AR volume, a "Detect 5G (needs phone state permission)" toggle that requests READ_PHONE_STATE, the attribution text from Global Constraints, the licence, and a source link.
  Permissions: CAMERA when entering AR. ACCESS_FINE_LOCATION on first launch, with a rationale. POST_NOTIFICATIONS (33+) before starting ambient. Denied location → the app still opens, AR shows "Location needed", and ambient runs live-only.

- [ ] **Step 1: Implement** the screens and the permission flow.
- [ ] **Step 2: Verify** with `./gradlew assembleDebug testDebugUnitTest` (all green) and `./gradlew lint` (no errors).
- [ ] **Step 3: Device check:** a fresh install with location denied doesn't crash. Add a key, and towers appear. Start ambient, lock the screen, walk, and hear changes. Stop it from the notification.
- [ ] **Step 4: Commit** `feat: home, settings and permissions`.

### Task 12: F-Droid readiness

**Files:**
- Create: `fastlane/metadata/android/en-US/{title.txt,short_description.txt,full_description.txt,changelogs/1.txt}`, `fastlane/metadata/android/en-US/images/icon.png`
- Modify: `app/build.gradle.kts` (`versionCode 1`, `versionName "0.1.0"`, release build without signing config in the repo, `dependenciesInfo { includeInApk = false; includeInBundle = false }`), `README.md` (build steps, submodule init, OpenCelliD key, the Pd tuning workflow)

- [ ] **Step 1: Write the metadata.** `full_description.txt` states that the app uses OpenCelliD, which needs a user API key (the reason for a likely `NonFreeNet` anti-feature, which the F-Droid reviewers decide).
- [ ] **Step 2: Verify that no proprietary dependencies are present:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i -E "gms|firebase|play|arcore|crashlytics"`. Expected: no output.
- [ ] **Step 3: Verify a clean build from a fresh clone:** `git clone --recursive . /tmp/sniffer-clean && cd /tmp/sniffer-clean && ./gradlew assembleRelease`. Expected: BUILD SUCCESSFUL (unsigned APK).
- [ ] **Step 4: Commit** `chore: F-Droid metadata`.
