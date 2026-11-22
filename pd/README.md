# Sniffer soundscape (Pure Data)

`sniffer.pd` is the soundscape the app plays (copied into the APK's assets at build time).
It uses **Pd vanilla objects only**, because libpd on Android has no externals.

## Tune it by ear on a laptop

1. Install Pd vanilla (macOS: `brew install --cask pd`).
2. Open `harness.pd`. It loads `sniffer.pd` as an abstraction.
3. Click `; pd dsp 1`, then a preset (forest / suburb / downtown), or type values into the
   number boxes. `; handover bang` and `; neighbour_event bang` fire the live events.
4. Edit `sniffer.pd`, save, and close and reopen the harness to hear the change.

## Inputs (sent by the app once per second)

| receive | range | drives |
|---|---|---|
| `density` | 0–1 | drone voices (1 → 6), filter cutoff, drone level, lead phrase probability |
| `signal` | 0–1 | static/crackle amount (weaker = more) |
| `nettype` | 0, 2, 3, 4, 5 | drone root and brightness (2G adds square timbre); 0 = no service, crackle thins out |
| `nearest` | metres | lead filter brightness (closer = brighter) |
| `neighbours` | count | (available, unused so far) |
| `handover` | bang | static burst + lead phrase |
| `neighbour_event` | bang | lead phrase |
| `master` | 0–1 | output volume |

## Render check

`render-test.pd` renders 10 s of "forest" and 10 s of "downtown" to `render-test.wav` without audio hardware:

    cd pd && pd -nogui -nosound -batch -open render-test.pd
