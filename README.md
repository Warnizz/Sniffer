# Sniffer

See the cell towers around you through your camera, and hear the network as an ambient
soundscape. Free software (GPL-3.0-or-later), with no Google services, built for F-Droid.

- **Look around:** a camera view with tower beacons placed in their real direction
  (GPS + compass/gyro, no ARCore). The tower you're connected to pulses. Tap a beacon for details.
- **Ambient:** a screen-off soundscape (Pure Data, via libpd). A drone thickens with tower
  density, radio static follows live signal quality, and synth phrases play when it gets
  dense or when your phone hands over to another tower.

Tower positions come from [OpenCelliD](https://opencellid.org) (CC BY-SA 4.0). Each user enters
their own free API key in settings. Without a key the soundscape still runs on live data.

## Building

Requirements: JDK 17, an Android SDK with platform 34 and 35, NDK `29.0.14206865`, CMake 3.22.1.

```sh
git clone --recursive <repo-url>
cd Sniffer
export ANDROID_HOME=/path/to/android/sdk   # required: the libpd included build reads it
./gradlew assembleDebug                     # or assembleRelease (unsigned)
./gradlew testDebugUnitTest                 # JVM unit tests
```

If you cloned without `--recursive`: `git submodule update --init --recursive`.

libpd comes from `external/pd-for-android` (git submodule, built from source with the NDK).
It's pinned to a commit that uses Android Gradle Plugin 8.13.x, matching this project.

## Sound design

The soundscape lives in [`pd/sniffer.pd`](pd/sniffer.pd) and is copied into the APK at build
time. Open `pd/harness.pd` in Pd vanilla to tune it on a laptop. See [`pd/README.md`](pd/README.md).

## Layout

| Package | What |
|---|---|
| `geo` | distances, bearings, tile grid |
| `towers` | OpenCelliD client, Room cache, repository |
| `live` | modem cell info, signal normalisation, handovers, matching |
| `sound` | density, smoothing, control mapping, libpd sink |
| `engine` | the engine that ties it together, foreground service |
| `ar` | projection math, orientation sensor, camera screen |
| `ui` | home, settings |

Design: `docs/superpowers/specs/`. Plan: `docs/superpowers/plans/`.
