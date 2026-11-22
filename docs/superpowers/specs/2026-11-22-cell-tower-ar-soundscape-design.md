# Sniffer — Cell Tower AR & Ambient Soundscape (Design)

Date: 2026-11-22
Status: Draft for approval

## Intent

A fun exploration toy for Android. You hold up your phone and see the cell
towers around you as beacons floating in their real-world direction, a bit
like Pokémon GO but for radio infrastructure. You can also put the phone in
your pocket with headphones on and hear the network: an ambient soundscape
that thickens as you walk into areas with more and closer towers, and reacts
live to what your phone is actually hearing.

It should be free and open source and publishable on F-Droid.

## Goals

1. **AR view:** camera preview with cell-tower beacons overlaid in their real
   compass direction and elevation, labelled with distance. Tap a beacon for
   details.
2. **Ambient mode:** screen-off soundscape that runs in the background,
   driven by a blend of database tower density and live radio data.
3. **FOSS + F-Droid:** builds from source with no proprietary dependencies.

## Non-goals (for now)

- Wi-Fi hotspots (considered; deferred — positioning needs WiGLE, which is
  non-free and rate-limited). Keep the code shaped so a Wi-Fi source could be
  added later, but don't build it.
- Collecting, scores, progression or any game layer.
- Tower position estimation from the user's own measurements.
- A map screen (may come later; not in scope).
- iOS.

## Anti-goals

- Depending on Google Play Services, ARCore, Firebase, or any proprietary
  SDK — that would block F-Droid.
- A soundscape that's annoying: harsh, repetitive, or with sudden loud
  jumps. It's an ambient piece you leave on for an hour.
- Draining the battery badly enough that people stop using ambient mode.
- Shipping any API key in the app or sending user location anywhere other
  than the tower-data query the user opted into.

## Constraints

- **Android only**, Kotlin. F-Droid build: no GMS, reproducible from source.
- **No ARCore.** "AR" is a sensor-fused overlay: camera preview + rotation
  vector sensor (compass/gyro) + GPS. Accuracy of a few degrees is fine;
  towers are far away.
- **Phones only see part of the network live.** `TelephonyManager` reports
  the serving cell and some neighbour cells, mostly on the user's own
  carrier; neighbours often lack full IDs. Live data is thin and noisy.
- **Tower positions come from OpenCelliD** (data CC-BY-SA 4.0; requires a
  free per-user API key). Positions are estimates, sometimes hundreds of
  metres off. Attribution required in-app.
- **Background running** requires a foreground service with a persistent
  notification (types `location` and `mediaPlayback`), and location
  permission granted while the app is in use.
- **libpd** (BSD) for audio, which pulls in the NDK build.

## Approaches considered (sound engine)

1. **Pure Data via libpd — chosen.** The soundscape is a Pd patch; the app
   sends control values into it. The sound can be designed and tweaked on a
   desktop in Pd with instant feedback, separate from app code. Drones,
   filtered noise and synth leads are Pd's home turf. Trade-off: older-style
   Android bindings and a native build step.
2. **Custom C++ synth on Oboe.** Fast, no dependencies, but every sound change
   is a code change and rebuild; sound design becomes slow.
3. **Layered recorded loops.** Quickest to sound good, but feels like mixing
   tracks rather than signals playing an instrument.

The user picked **1** because the sound is the heart of ambient mode and will
need lots of by-ear iteration.

## Decided

### Data sources

- **Database layer:** OpenCelliD towers around the user's GPS position. The
  user enters their own OpenCelliD API key in settings (no bundled key).
  Results are cached on-device (SQLite/Room) by area so walking around a
  known area needs no network. *(Builder's call: query by small bounding-box
  tiles to fit OpenCelliD's area limit; optionally allow importing a
  per-country CSV download for fully offline use.)*
- **Live layer:** `TelephonyManager.getAllCellInfo()` /
  `requestCellInfoUpdate()` — serving cell identity, signal strength (RSRP /
  RSSI / dBm normalised), network type (2G/3G/4G/5G), neighbour cells, and
  serving-cell changes (handovers).
- **All carriers' towers count** (user's decision), for both the sound and
  the AR view.
- Where a live cell's identity matches a database tower, use the database
  position for it. Live cells with no database match can't be placed in AR
  and only feed the sound.
- *(My call)* Neighbour cells often report only PCI/EARFCN. Match them
  heuristically (same carrier, same PCI and band, nearest DB tower) and
  mark such matches as "probable" in the details sheet.
- *(My call)* Request `READ_PHONE_STATE` as an optional permission so 5G NSA
  can be detected (otherwise it reports as LTE). On dual-SIM phones the
  default data SIM drives `signal`, `handover` and `nettype`.

### AR view

- Camera preview (CameraX) full screen.
- Beacons drawn on top at the tower's bearing and elevation from the user,
  computed from GPS + rotation vector sensor. *(My call)* Elevation assumes
  flat ground and a fixed ~30 m mast height, so near towers sit higher above
  the horizon and far ones close to it. No terrain data. Size/opacity fall off with
  distance. Label: distance, and network type.
- *(My call)* The user's own carrier's towers are drawn bright and other
  carriers' dimmed, to keep dense areas readable. Beyond that, clutter
  handling (caps, clustering) is the builder's call.
- **The soundscape also plays in the AR view** (user's decision), quietly by
  default with a mute toggle. AR view and ambient mode share one data +
  audio service.
- The serving cell's beacon is highlighted; neighbours the phone hears live
  are marked differently from database-only towers.
- Tap a beacon: details sheet (radio type, MCC/MNC, LAC/TAC, cell ID,
  estimated range, distance, live signal if heard, data source).
- If no API key is set, AR still works for nothing but the live serving
  cell's info — show a clear prompt to add a key.

### Ambient mode

- Started from a button in the app; runs as a foreground service with a
  notification (play/pause/stop). Keeps running with the screen off.
- **Blend of database and live**, as agreed:
  - **Database layer = the bed.** Tower density around the user, weighted by
    distance (e.g. sum of a falloff over towers within ~2 km), smoothed over
    time. Sets overall intensity.
  - **Live layer = texture.** Serving-cell signal, handovers, neighbour
    cells and network type add real-time character on top.

### Sound character (user's words: "ambient drone with crackly radio static and some synth leads, gets denser downtown")

| Sound element | Driven by |
|---|---|
| **Drone** — always present, thickens (more voices, wider, richer harmonics) | Database density (bed) |
| **Radio static / crackle** — grit and amount | Live serving-cell signal quality (weaker = more crackle); spikes at handovers |
| **Synth leads** — phrases fade in as density rises; neighbour cells and handovers trigger phrases | Density threshold + live neighbour/handover events |
| **Overall colour / timbre** | Network type (2G/3G/4G/5G) |

*(My call)* With no API key, or in an uncached area offline, ambient mode
still runs: `density` falls back to a live-derived estimate (neighbour count
and signal), and the notification says database data is missing. The patch
only ever sees `density`, so it doesn't need a second mode.

Forest ≈ near-silent hum; downtown ≈ dense crackling wall with melodies
drifting through. All changes are smoothed — no sudden loud jumps.

### Control interface (app → Pd)

The app sends a small, fixed set of named messages into the patch, so the
patch and the app can evolve separately:

- `density` 0–1 (smoothed database intensity)
- `nearest` metres to nearest known tower
- `signal` 0–1 (normalised serving-cell strength)
- `nettype` 2/3/4/5
- `neighbours` count of live neighbour cells
- `handover` bang on serving-cell change
- `neighbour_event` bang when a new neighbour appears

*(My call)* The patch is bundled in the APK for v1; no loading patches from
phone storage. The repo includes the patch (`pd/`) and a desktop test harness patch with
sliders for each control, so the sound can be designed in Pd without the
phone.

### Project

- Kotlin, Jetpack Compose UI, CameraX, Room, libpd (pd-for-android).
- Licence: GPL-3.0-or-later.
- Permissions: camera, fine location, foreground service (location +
  media playback), notifications, internet (OpenCelliD only).
- Settings: OpenCelliD key, search radius, ambient volume.
- In-app attribution for OpenCelliD (CC-BY-SA).

## Left to the builder

- Exact density formula, falloff curve and smoothing constants.
- Signal normalisation per radio type.
- Polling rates for GPS and cell info in ambient mode, tuned for battery
  (e.g. slower when stationary).
- The actual Pd patch design within the sound character above.
- Beacon visual style.
- Cache tiling, expiry, and whether to support country CSV import in v1.
- Min SDK (suggest 29 for `requestCellInfoUpdate`) and module structure.
- F-Droid metadata and build recipe.
