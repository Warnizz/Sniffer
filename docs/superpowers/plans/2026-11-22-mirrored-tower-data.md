# Mirrored Tower Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users get tower data without an OpenCelliD account. A monthly GitHub Actions job mirrors per-country exports to GitHub Releases, and the app downloads and imports the user's country.

**Architecture:** A stdlib-only Python script (`tools/mirror/mirror.py`) slims each export to the columns the app uses and writes `index.json`. The workflow runs it with the maintainer's token and uploads to the `towers-latest` release. In the app, `CountryData.kt` parses the index and CSV, `CountryImporter` streams a gzipped file into Room, and the repository treats imported cells as data, with a personal key topping them up. Home and Settings drive downloads.

**Tech Stack:** Python 3 (stdlib, unittest), GitHub Actions + `gh` CLI, Kotlin/Room/OkHttp (existing).

**Spec:** `docs/superpowers/specs/2026-11-22-mirrored-tower-data-design.md` (builds on `2026-11-22-cell-tower-ar-soundscape-design.md`)

## Global Constraints

- Application ID / namespace / package: `io.github.warnizz.sniffer`.
- Release tag `towers-latest`. Base URL `https://github.com/Warnizz/Sniffer/releases/download/towers-latest/`.
- Mirror columns, in order: `radio,mcc,net,area,cell,unit,lon,lat,range,samples` (header row included). File name `<mcc>.csv.gz`.
- `index.json`: `{"generated": "<ISO date>", "countries": {"<mcc>": {"file": "<mcc>.csv.gz", "bytes": N, "rows": N}}}`.
- Attribution, exact, wherever data is credited: "Cell tower data from OpenCellID · CC BY-SA 4.0. Changes: unused columns removed." with links to https://opencellid.org and https://creativecommons.org/licenses/by-sa/4.0/.
- No OpenCelliD token in the repo or the app. The workflow reads `secrets.OPENCELLID_TOKEN`.
- The app never sends location to fetch country data. The MCC comes from the serving cell.
- No country download on a metered network without the user tapping a button that shows the size.

## Review Focus

1. **Malformed or partial export rows** (empty `unit`, header present or absent, bad numbers, lat/lon at 0,0): skipped, not fatal. Pinned in Task 2 (`slim_skipsBadRows`) and Task 3 (`parseCountryCsv_skipsJunk`).
2. **Importing a country must not erase the other country's cells** in a shared border tile, and an API fill must not erase imported cells. Pinned in Task 3 (`store_upsert_keepsOtherCells`).
3. **No key but country data present:** status OK and hasData true, so the sound uses DB density. Pinned in Task 3 (`repository_noKey_withCountryData_ok`).
4. **Download interrupted, or the index/file is 404:** no partial import is recorded as complete, the error is shown, and the next attempt redoes it. Pinned in Task 4 (`importer_failedDownload_notMarkedImported`).
5. **Removing a country** deletes its cells and record. Pinned in Task 3 (`store_removeCountry`).

---

### Task 1: Rename application ID

**Files:** move `app/src/{main,test}/java/io/github/snifferapp/sniffer` → `.../io/github/warnizz/sniffer`. Modify `app/build.gradle.kts` (`namespace`, `applicationId`) and every `package` / `import`.

- [ ] **Step 1:** `git mv` the dirs, then sed `io.github.snifferapp.sniffer` → `io.github.warnizz.sniffer` across `app/` and `README.md`.
- [ ] **Step 2: Verify** with `./gradlew assembleDebug testDebugUnitTest`. Expected: BUILD SUCCESSFUL, and all existing tests pass. `grep -r snifferapp app README.md` gives no output.
- [ ] **Step 3: Commit** `chore: application id io.github.warnizz.sniffer`.

### Task 2: Mirror script and workflow

**Files:**
- Create: `tools/mirror/mirror.py`, `tools/mirror/test_mirror.py`, `tools/mirror/LICENSE-DATA.txt`, `.github/workflows/mirror-towers.yml`

**Interfaces:**
- Produces (Python):
  ```python
  COLUMNS = ["radio","mcc","net","area","cell","unit","lon","lat","range","samples"]
  def slim(lines: Iterable[str]) -> Iterator[str]          # header + valid rows in COLUMNS order
  def build_index(entries: dict[str, tuple[int,int]], generated: str) -> dict  # mcc -> (bytes, rows)
  def main(argv) -> int   # mirror.py <in_dir_of_mcc_csv_gz> <out_dir>
  ```
  `slim` accepts the 14-column OpenCelliD layout (`radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal`), with or without a header. It drops a row with fewer than 10 fields, a non-numeric mcc/net/area/cell/lon/lat, |lat|>90, |lon|>180, or lat==lon==0. An empty `unit` stays empty.
- Workflow: `on: schedule: cron "0 4 3 * *"` plus `workflow_dispatch`. Steps:
  1. Fetch `https://opencellid.org/downloads.php?token=$TOKEN` and extract the per-MCC links with a regex for `file=(\d{3})\.csv\.gz`. Fail if none match.
  2. Download each one with `curl -fsSL` and a 2 s sleep between them (a single download per file per run).
  3. Run `mirror.py`.
  4. Run `gh release create towers-latest` (if missing), then `gh release upload towers-latest out/* --clobber`, and also create a dated release `towers-YYYY-MM`.
  - Permissions: `contents: write`.

- [ ] **Step 1: Write tests** in `test_mirror.py` (unittest). `slim_withHeader_keepsColumns` (header row out == COLUMNS). `slim_withoutHeader_works`. `slim_dropsExtraColumns` (14 → 10 fields). `slim_skipsBadRows` (short row, "abc" mcc, lat 95, 0/0). `slim_keepsEmptyUnit`. `build_index_shape`. `main_writesGzAndIndex` (temp dirs: 238.csv.gz in → out has 238.csv.gz, index.json, LICENSE-DATA.txt; the index reports rows).
- [ ] **Step 2: Run** `python3 -m unittest tools/mirror/test_mirror.py`. Expected: FAIL (import error).
- [ ] **Step 3: Implement** `mirror.py`, the LICENSE-DATA.txt (attribution and the CC BY-SA 4.0 link), and the workflow.
- [ ] **Step 4: Run** the tests. Expected: OK. Check the workflow YAML parses: `python3 -c "import yaml"` if available, else `ruby -ryaml -e 'YAML.load_file(".github/workflows/mirror-towers.yml")'`.
- [ ] **Step 5: Commit** `feat: monthly OpenCelliD mirror to GitHub Releases`.

### Task 3: Country data parsing and store changes

**Files:**
- Create: `towers/CountryData.kt`
- Modify: `towers/TowerDb.kt` (version 2; `CountryEntity(mcc: Int PK, rows: Int, importedAtMs: Long, generated: String)`; DAO upsert/remove/list), `towers/TowerStore.kt`, `towers/TowerRepository.kt`, `SnifferApp.kt` (`fallbackToDestructiveMigration`)
- Test: `towers/CountryDataTest.kt`, `towers/RoomTowerStoreTest.kt`, `towers/TowerRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class CountryFile(val mcc: Int, val file: String, val bytes: Long, val rows: Int)
  data class CountryIndex(val generated: String, val countries: Map<Int, CountryFile>)
  fun parseCountryIndex(json: String): CountryIndex
  fun parseCountryCsv(lines: Sequence<String>): Sequence<Cell>  // header skipped; unit parsed; junk skipped
  data class CountryRecord(val mcc: Int, val rows: Int, val importedAtMs: Long, val generated: String)
  interface TowerStore {  // additions
      suspend fun upsertCells(cells: List<Cell>)            // no deletes
      suspend fun putCountry(record: CountryRecord)
      suspend fun countries(): List<CountryRecord>
      suspend fun removeCountry(mcc: Int)                   // deletes cells with that mcc + record
  }
  ```
  Change `putTile` to upsert only (no delete), so API fills never erase imported cells.
  Repository: `hasData` = some covering tile was fetched **or** `cellsInTiles` is non-empty. With no key: status `OK` when cells exist, `NO_KEY` otherwise.

- [ ] **Step 1: Write tests.** `parseCountryIndex_reads`. `parseCountryCsv_readsUnit` (LTE row with unit 123 → `unit == 123`). `parseCountryCsv_emptyUnit_null`. `parseCountryCsv_skipsJunk`. `store_upsert_keepsOtherCells` (tile has cell A; upsert B gives both; putTile with C gives A, B, C). `store_removeCountry` (cells of mcc 238 gone, 240 kept, record gone). `repository_noKey_withCountryData_ok`. Update `room_putThenRead_roundTrips`: replacing no longer drops cells.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*towers*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the full suite. Expected: PASS.
- [ ] **Step 5: Commit** `feat: country data parsing and store support`.

### Task 4: Country importer

**Files:**
- Create: `towers/CountryImporter.kt`
- Test: `towers/CountryImporterTest.kt` (MockWebServer + in-memory fake store)

**Interfaces:**
- Consumes: `parseCountryIndex`, `parseCountryCsv`, `TowerStore`.
- Produces:
  ```kotlin
  sealed interface ImportResult { data class Ok(val rows: Int) : ImportResult; data class Failed(val reason: String) : ImportResult
      data object NotAvailable : ImportResult }
  class CountryImporter(http: OkHttpClient, store: TowerStore, nowMs: () -> Long,
                        baseUrl: String = "https://github.com/Warnizz/Sniffer/releases/download/towers-latest") {
      suspend fun index(): CountryIndex?                    // null on failure
      suspend fun import(mcc: Int, progress: (Int) -> Unit = {}): ImportResult  // streams gunzip → batches of 2000 upserts
      suspend fun updateAvailable(rec: CountryRecord): Boolean  // index.generated > rec.generated
  }
  ```
  The country record is written only after every batch succeeds.

- [ ] **Step 1: Write tests.** `importer_importsRowsAndRecords` (gz body with 3 rows gives 3 cells upserted and a record with rows 3). `importer_mccNotInIndex_notAvailable`. `importer_failedDownload_notMarkedImported` (index ok, file 500 → Failed, no record). `importer_truncatedGzip_failed_noRecord`. `updateAvailable_newerGenerated_true`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the full suite. Expected: PASS.
- [ ] **Step 5: Commit** `feat: country data importer`.

### Task 5: Download flow, settings, attribution

**Files:**
- Create: `towers/CountryDataManager.kt` (app-scoped: state flow of `{ servingMcc, records, index, busy: Int?, progress, error }`; `download(mcc)`, `remove(mcc)`, `autoUpdate()` on app start if the last check is more than 7 days old and the network is unmetered)
- Modify: `SnifferApp.kt`, `ui/HomeScreen.kt` (card: "Download tower data for MCC 238 — 4.2 MB" when the serving MCC has no record; shows progress and errors), `ui/SettingsScreen.kt` (downloaded countries list with remove, add-by-MCC field, "Check for updates", the key described as optional), `ATTRIBUTION`, `README.md`, `fastlane/.../full_description.txt`
- Test: `towers/CountryDataManagerTest.kt` (`autoUpdate_skipsOnMetered`, `autoUpdate_skipsWithin7Days`, `download_refreshesRecords`)

- [ ] **Step 1: Write tests.**
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** The engine needs no change: the repository already serves imported cells.
- [ ] **Step 4: Run** `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: green, with no lint errors.
- [ ] **Step 5: Commit** `feat: download tower data per country`.

### Task 6: Create repo and push

- [ ] **Step 1:** `gh repo create Warnizz/Sniffer --public --source . --remote origin --description "See and hear the cell towers around you (Android, FOSS)"`.
- [ ] **Step 2:** `git push -u origin main feature/cell-tower-ar`.
- [ ] **Step 3: Verify** with `gh repo view Warnizz/Sniffer --json visibility,defaultBranchRef`. Expected: PUBLIC, main.
- [ ] Note for the user: scheduled and manual workflows only run from the default branch, so the mirror needs the branch merged and an `OPENCELLID_TOKEN` secret.
