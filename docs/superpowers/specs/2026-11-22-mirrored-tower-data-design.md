# Sniffer — Mirrored Tower Data (Design)

Date: 2026-11-22
Status: Draft for approval
Builds on: `2026-11-22-cell-tower-ar-soundscape-design.md`

## Intent

Users shouldn't need an OpenCelliD account. The project mirrors OpenCelliD's per-country
exports on GitHub Releases, and the app downloads the user's country once and works offline.
A personal API key stays available as an optional extra for live, fresher data.

## Terms (checked 2026-11-22)

- The data is CC BY-SA 4.0, including the bulk downloads. Redistribution is allowed with
  attribution, ShareAlike, notices kept, and no added restrictions.
- No separate Terms of Service is published (`opencellid.org/terms` returns 403, and the site
  links no terms page).
- Each user may download each file twice a day, and exports refresh daily. A monthly mirror
  stays far inside that.
- Required visible credit: "Cell tower data from OpenCellID · CC BY-SA 4.0", linking to
  opencellid.org and the licence.
- Courtesy (optional, the user's call): email hello@opencellid.org about the mirror.

## Goals

1. **Mirror job:** a scheduled GitHub Actions workflow (monthly, plus manual trigger) in the app's
   repo. It downloads OpenCelliD per-country (MCC) exports using the maintainer's token from a
   repo secret, and publishes them as assets on a rolling release `towers-latest` (plus a dated
   tag for history). Each asset is a gzipped CSV, one file per MCC, together with a `LICENSE-DATA.txt`
   and a manifest `index.json` (mcc → file name, size, row count, export date).
2. **App download:** the app reads the serving cell's MCC, fetches `index.json`, downloads that
   country's file, and imports it into the existing Room cache. Settings lists the downloaded
   countries, lets the user add more (for borders and travel) and remove them, and has a
   "Check for updates" action. The app checks for an update at most once a week, and only on
   unmetered networks.
3. **Data precedence:** imported country data marks its tiles as covered. A personal API key, if set,
   still fills or refreshes tiles live on top. With no key and no country file, the app behaves
   as it does now (live-only sound).
4. **PCI matching:** the export's `unit` column fills `Cell.unit`, so the PCI-based "probable"
   neighbour matching starts working.
5. **Attribution:** update the in-app credit, the README and the F-Droid description to the
   required wording.

## Non-goals

- Worldwide download in one file.
- Daily diffs (monthly full exports are enough for a toy).
- Hosting anywhere but GitHub Releases.

## Anti-goals

- No OpenCelliD token in the app or the repo (only in the Actions secret).
- The app never sends the user's location anywhere to get country data: the MCC comes from the modem.
- No large surprise downloads on mobile data. Show the size and ask first when on a metered
  network.

## Decided

- Hosting: GitHub Releases in the app's own repo (user's decision).
- Repo: `github.com/Warnizz/Sniffer`, public (needed for F-Droid and unauthenticated downloads).
  *(My proposal.)*
- Application ID: change the placeholder `io.github.snifferapp.sniffer` to
  `io.github.warnizz.sniffer` to match the repo, before any release. *(My proposal.)*
- Download URL: `https://github.com/Warnizz/Sniffer/releases/download/towers-latest/<file>`.
- The F-Droid listing keeps describing OpenCelliD. The mirror is the project's own hosting, so a
  NonFreeNet tag is less likely (the reviewers decide).

## Left to the builder

- OpenCelliD's exact per-country download URL (verify with the token in the workflow; fail the
  job loudly if the format changes).
- Import strategy (streaming CSV → Room in batches, on a background worker), and how tiles get
  marked as covered.
- Whether to strip columns or round values to shrink files (if so, add the "Changes:" note to
  the attribution as the licence page asks).
